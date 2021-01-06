import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefectChecker {
    public boolean hasUnchechedExternalCalls = false;
    public boolean hasStrictBalanceEquality = false;
    public boolean hasTransactionStateDependency = false;
    public boolean hasBlockInfoDependency = false;
    public boolean hasGreedyContract = false;
    public boolean hasDoSUnderExternalInfluence = false;
    public boolean hasNestCall = false;
    public boolean hasReentrancy = false;

    public BinaryAnalyzer binaryAnalyzer = null;
    public DefectChecker(BinaryAnalyzer binaryAnalyzer){
        this.binaryAnalyzer = binaryAnalyzer;
    }

    public void detectAllSmells(){

        this.hasUnchechedExternalCalls = detectUnchechedExternalCalls();
        this.hasStrictBalanceEquality = detectStrictBalanceEquality();
        this.hasTransactionStateDependency = detectTransactionStateDependency();
        this.hasBlockInfoDependency = detectBlockInfoDependency();
        this.hasGreedyContract = detectGreedyContract();
        this.hasDoSUnderExternalInfluence = detectDoSUnderExternalInfluence();
        this.hasNestCall = detectNestCall();
        this.hasReentrancy = detectReentrancy();
    }


    public boolean detectUnchechedExternalCalls(){
        if(!binaryAnalyzer.allInstrs.contains("CALL"))
            return false;
        boolean res = false;
        boolean start = false;
        String callPC = "";
        for(String event : binaryAnalyzer.stackEvents){
            String eventLog = event.split(" ==> ")[2];
            if(eventLog.startsWith("CALL_")){
                start = true;
                callPC = eventLog.split(" ")[0];
                continue;
            }
            if(start){
                //CALL instr does not checked by ISZERO
                if(!eventLog.contains(callPC)){
                    res = true;
                    break;
                }
                if(eventLog.startsWith("ISZERO_")){
                    String top = eventLog.split(" ")[0];
                    //if CALL instr is checked by ISZERO, it means it's a legal CALL
                    if(top.contains(callPC)){
                        callPC = "";
                        start = false;
                    }
                }
            }
        }
        return res;
    }

    public boolean detectStrictBalanceEquality(){
        if(!binaryAnalyzer.allInstrs.contains("BALANCE"))
            return false;
        boolean res = false;
        boolean startBalance = false;
        boolean startEQ = false;
        String balancePC = "";
        String eqPC = "";
        for(String event : binaryAnalyzer.stackEvents){
            String eventLog = event.split(" ==> ")[2].split(" ")[0];
            if(eventLog.startsWith("BALANCE_")){
                startBalance = true;
                balancePC = eventLog.split(" ")[0];
                continue;
            }
            if(startBalance){

                if(!eventLog.contains(balancePC)){
                    startBalance = false;
                    startEQ = false;
                    balancePC = "";
                    eqPC = "";
                    continue;
                }

                if(startEQ){
                    if(!eventLog.contains(eqPC)){
                        startBalance = false;
                        startEQ = false;
                        balancePC = "";
                        eqPC = "";
                        continue;
                    }
                    //Balance && EQ && ISZERO ==> has code smell.
                    if(eventLog.startsWith("ISZERO_")){
                        String top = eventLog.split(" ")[0];

                        //may be there contrains other eq, so we should check it.
                        if(top.contains(eqPC)){
                            res = true;
                            break;
                        }
                    }
                }

                if(eventLog.startsWith("EQ_")){
                    startEQ = true;
                    eqPC = eventLog.split(" ")[0];
                }
            }
        }
        return res;
    }

    public boolean detectTransactionStateDependency(){
        if(!binaryAnalyzer.allInstrs.contains("ORIGIN"))
            return false;
        boolean res = false;
        boolean start = false;
        String originPC = "";
        for(String event : binaryAnalyzer.stackEvents){
            String eventLog = event.split(" ==> ")[2];
            if(eventLog.startsWith("ORIGIN_")){
                start = true;
                originPC = eventLog.split(" ")[0];
                continue;
            }

            if(start){
                if(!eventLog.contains(originPC)){
                    start = false;
                    originPC = "";
                    continue;
                }
                if(eventLog.startsWith("EQ_")){
                    String top = eventLog.split(" ")[0];
                    if(top.contains(originPC)){
                        res = true;
                        break;
                        //EXAMPLE: EQ_1210(AND_1209(PUSH20_1188,ORIGIN_1187),AND_1186(PUSH20_1165,AND_1164(PUSH20_1143,DIV_1142(SLOAD_1135(0_1131),1_1140))))
//                        Pair<String, String> splitedInstr = Utils.splitInstr2(top);
//                        String address = "";
//                        if(splitedInstr.getFirst().contains(originPC))
//                            address = splitedInstr.getSecond();
//                        else if(splitedInstr.getSecond().contains(originPC))
//                            address = splitedInstr.getFirst();
//                        if(address.startsWith("AND_")){
//                            Pair<String, String> splitedAddr = Utils.splitInstr2(address);
//                            if((splitedAddr.getFirst().contains("PUSH20_") && splitedAddr.getSecond().contains("SLOAD_")) ||
//                                    (splitedAddr.getSecond().contains("PUSH20_") && splitedAddr.getFirst().contains("SLOAD_")))
//                            {
//                                res = true;
//                                break;
//                            }
//                        }
                    }

                }
            }
        }
        return res;
    }

    public boolean detectBlockInfoDependency(){
        String[] blockInstr = new String[]{"BLOCKHASH", "COINBASE", "NUMBER", "DIFFICULTY", "GASLIMIT"};
        boolean res = false;
        for(Map.Entry<Integer, BasicBlock> entry : this.binaryAnalyzer.pos2BlockMap.entrySet()){
            BasicBlock block = entry.getValue();
            if(block.conditionalJumpExpression.length() > 1){
                for(String instr : blockInstr){
                    if(block.conditionalJumpExpression.contains(instr)){
                        res = true;
                        break;
                    }
                }
            }
        }
        return res;
    }

    public boolean isPayable(Integer startPos){
        if(startPos == -1)
            return false;
        boolean payable = true;
        BasicBlock block = this.binaryAnalyzer.pos2BlockMap.get(startPos);
        BasicBlock fall = this.binaryAnalyzer.pos2BlockMap.get(block.fallPos);
        String conditionalJumpExpression = block.conditionalJumpExpression;
        if(conditionalJumpExpression.contains("CALLVALUE")){
            if(Pattern.matches("ISZERO_[0-9]+?\\(CALLVALUE_[0-9]+?\\)", conditionalJumpExpression)){
                if(fall.jumpType == BasicBlock.TERMINAL){
                    payable = false;
                }
            }
        }
        if(conditionalJumpExpression.equals("") && block.jumpType == BasicBlock.TERMINAL)
            payable = false;
        return payable;

    }

    public boolean detectGreedyContract(){
        //if a contract can receive money(contains payable function), but cannot send ETH(do not contains CALL/SELFDESTRUCT)

        if(this.binaryAnalyzer.allInstrs.contains("SELFDESTRUCT"))
            return false;
        boolean payable = false;
        boolean canSentMoney = false;
        if(isPayable(this.binaryAnalyzer.fallbackPos))
            payable = true;
        for(Integer pos : this.binaryAnalyzer.publicFunctionStartList){
            if(isPayable(pos))
                payable = true;
        }
        for(Map.Entry<Integer, BasicBlock> entry : this.binaryAnalyzer.pos2BlockMap.entrySet()){
            BasicBlock block = entry.getValue();
//            if(block.moneyCall)
            if(block.instrString.toString().contains("CALL "))
                canSentMoney = true;
        }
        if(payable && !canSentMoney)
            return true;
        return false;
    }

    public boolean detectDoSUnderExternalInfluence(){
        for(Map.Entry<Integer, BasicBlock> entry : this.binaryAnalyzer.pos2BlockMap.entrySet()){
            BasicBlock block = entry.getValue();
            if(block.isCircle){

                if(block.jumpType == BasicBlock.CONDITIONAL && block.moneyCall){
                    BasicBlock fall = this.binaryAnalyzer.pos2BlockMap.get(block.fallPos);
                    if(fall.jumpType == BasicBlock.TERMINAL)
                        return true;
                }
            }
        }
        return false;
    }

    public boolean detectNestCall(){
        if(!this.binaryAnalyzer.allInstrs.contains("CALL"))
            return false;
        if(!this.binaryAnalyzer.allInstrs.contains("SLOAD"))
            return false;
        int slotID = -1;
        boolean slotSizeLimit = false; //if do not limit slot size && have CALL ==> Nest Call
        boolean hasCall = false;
        for(Map.Entry<Integer, BasicBlock> entry : this.binaryAnalyzer.pos2BlockMap.entrySet()){
            BasicBlock block = entry.getValue();
            if(block.isCircleStart){
                String condition = block.conditionalJumpExpression;
                if(!condition.contains("SLOAD"))
                    continue;
                slotID = Utils.getSlotID(condition);
                if(slotID == -1)
                    continue;

                //BFS-travel to circle body and detect whether a jumpCondition contains slotID.
                Queue<BasicBlock> que = new LinkedList<>();
                HashSet<Integer> visited = new HashSet<>();
                visited.add(block.startBlockPos);
                que.offer(this.binaryAnalyzer.pos2BlockMap.get(block.conditionalJumpPos));
                que.offer(this.binaryAnalyzer.pos2BlockMap.get(block.fallPos));
                while(!que.isEmpty()){
                    BasicBlock topBlock = que.poll();
                    if(topBlock.conditionalJumpExpression.length() > 0){
                        if(topBlock.conditionalJumpExpression.contains("SLOAD_")
                                && (topBlock.conditionalJumpExpression.contains("LT") ||
                                topBlock.conditionalJumpExpression.contains("GT"))){
                            int id = Utils.getSlotID(topBlock.conditionalJumpExpression);
                            if(id == slotID) {
                                String[] tmp = topBlock.instrString.toString().split(" ");
                                int idx = 0;
                                for(idx = 0;idx < tmp.length;idx++){
                                    if(tmp[idx].equals("SLOAD"))
                                        break;
                                }
                                if(idx > 2){
                                    if(tmp[idx-1].startsWith("DUP") && tmp[idx-2].startsWith("PUSH")){
                                        slotSizeLimit = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
//                    if(topBlock.instrString.toString().contains("CALL ")){
                    if(topBlock.moneyCall){
                        hasCall = true;
                    }

                    if(topBlock.jumpType == BasicBlock.CONDITIONAL){
                        if(topBlock.conditionalJumpPos > 0){
                            BasicBlock child1 = this.binaryAnalyzer.pos2BlockMap.get(topBlock.conditionalJumpPos);
                            if(child1.isCircle && !visited.contains(child1.startBlockPos))
                                que.offer(child1);
                            visited.add(child1.startBlockPos);
                        }
                        BasicBlock child2 = this.binaryAnalyzer.pos2BlockMap.get(topBlock.fallPos);
                        if(child2.isCircle && !visited.contains(child2.startBlockPos))
                            que.offer(child2);
                        visited.add(child2.startBlockPos);
                    }
                    else if(topBlock.jumpType == BasicBlock.UNCONDITIONAL){
                        if(topBlock.unconditionalJumpPos > 0){
                            BasicBlock child = this.binaryAnalyzer.pos2BlockMap.get(topBlock.unconditionalJumpPos);
                            if(child.isCircle && !visited.contains(child.startBlockPos))
                                que.offer(child);
                            visited.add(child.startBlockPos);
                        }
                    }
                    else if(topBlock.jumpType == BasicBlock.FALL){
                        BasicBlock child = this.binaryAnalyzer.pos2BlockMap.get(topBlock.fallPos);
                        if(child.isCircle && !visited.contains(child.startBlockPos))
                            que.offer(child);
                        visited.add(child.startBlockPos);
                    }
                }

                if(slotSizeLimit)
                    return false;
            }
        }

        if(hasCall)
            return true;
        return false;
    }


//    public void findCallPath(ArrayList<Integer> currentPath, BasicBlock block, HashSet<Integer> visited){
//        visited.add(block.startBlockPos);
//        if(block.instrString.toString().contains("CALL ")){
//            this.binaryAnalyzer.allCallPath.add(currentPath);
////            return;
//        }
//        if(block.jumpType == BasicBlock.CONDITIONAL){
//            int left_branch = block.conditionalJumpPos;
//            if(!visited.contains(left_branch) && left_branch > 0) {
//                ArrayList<Integer> newPath = (ArrayList<Integer>) currentPath.clone();
//                newPath.add(left_branch);
//                findCallPath(newPath, this.binaryAnalyzer.pos2BlockMap.get(left_branch), visited);
//            }
//
//            int right_branch = block.fallPos;
//            if(!visited.contains(right_branch)) {
//                ArrayList<Integer> newPath = (ArrayList<Integer>) currentPath.clone();
//                newPath.add(right_branch);
//                findCallPath(newPath, this.binaryAnalyzer.pos2BlockMap.get(right_branch), visited);
//            }
//
//        }
//
//        else if(block.jumpType == BasicBlock.UNCONDITIONAL){
//            int jumpPos = block.unconditionalJumpPos;
//            if(!visited.contains(jumpPos) && jumpPos > 0) {
//                ArrayList<Integer> newPath = (ArrayList<Integer>) currentPath.clone();
//                newPath.add(jumpPos);
//                findCallPath(newPath, this.binaryAnalyzer.pos2BlockMap.get(jumpPos), visited);
//            }
//        }
//
//        else if(block.jumpType == BasicBlock.FALL){
//            int jumpPos = block.fallPos;
//            if(!visited.contains(jumpPos)) {
//                ArrayList<Integer> newPath = (ArrayList<Integer>) currentPath.clone();
//                newPath.add(jumpPos);
//                findCallPath(newPath, this.binaryAnalyzer.pos2BlockMap.get(jumpPos), visited);
//            }
//
//        }
//
//    }

    public boolean detectReentrancy(){
        //TODO: multiple CALL or SLOAD in one path
        //Currently, we can only detect one SLOAD, SSTORE, CALL in one path
        if(!this.binaryAnalyzer.allInstrs.contains("CALL"))
            return false;
        if(!this.binaryAnalyzer.allInstrs.contains("SLOAD"))
            return false;
        //Step1: travel all paths and select paths with "CALL" instr
        //Step2: obtain all path conditions on the selected paths
        //Step3: if a condition contains "SLOAD", then obtain its address.
        //Step4: detect whether the address is modified before the "CALL" instr.(SSTORE_PC && CALL_PC)

//        findCallPath(new ArrayList<>(), this.binaryAnalyzer.pos2BlockMap.get(0), new HashSet<>());
        for(ArrayList<Integer> path : this.binaryAnalyzer.allCallPath){
            String address = "";
            int callPC = -1;
            int sstorePC = -1;
            int sloadPC = -1;
            boolean gasLimited = false;
            boolean legalTransferAmount = true;
            ArrayList<Pair<Integer, Integer>> legalRange = new ArrayList<>();

            //get SLOAD address
            for(Integer blockID : path){
                BasicBlock block  = this.binaryAnalyzer.pos2BlockMap.get(blockID);
                legalRange.add(new Pair<>(block.startBlockPos, block.endBlockPos));
                if(block.conditionalJumpExpression.length() > 1) {
                    if(block.conditionalJumpExpression.contains("SLOAD")){
                        address = Utils.getSlotAddress(block.conditionalJumpExpression);
                        for(Pair<Integer, Pair<String, String>> instr : block.instrList){
                            if(instr.getSecond().getFirst().equals("SLOAD"))
                               sloadPC = instr.getFirst();
                        }
                    }
                }
            }

            //this path does not read from storage ==> this path does not have reentrancy bug
            if(sloadPC == -1)
                continue;

            for(int i = 0; i < this.binaryAnalyzer.stackEvents.size()-1; i++) {
                String[] eventLog = this.binaryAnalyzer.stackEvents.get(i).split(" ==> ");

                //get SSTORE PC;
                if(eventLog[0].equals("SSTORE")){
                    int pc = Integer.valueOf(eventLog[1]);

                    //this SSTORE is in current range
                    if(Utils.legalRange(pc, legalRange)){
                        String[] tmp = this.binaryAnalyzer.stackEvents.get(i-1).split(" ==> ");
                        String top = tmp[2].split(" ")[0];
                        if(top.length() > 0){
                            top = top.replaceAll("_[0-9]+", "");
                            //SSTORE read the same address with SLOAD, if sstorePC < CallPC ==> do not have reentrancy
                            if(top.length() > 0 && top.equals(address)){
                                sstorePC = Integer.valueOf(eventLog[1]);
                            }
                        }
                    }
                }

                if(eventLog[0].equals("CALL")){
                    int pc = Integer.valueOf(eventLog[1]);

                    //detect whether this CALL belongs to current path
                    if(Utils.legalRange(pc, legalRange)){
                        String[] tmp = this.binaryAnalyzer.stackEvents.get(i-1).split(" ==> ");
                        String top = tmp[2].split(" ")[0];
                        if(top.length() > 0){
                            //detect gas limitation, if CALL is created by send or transfer, then it will limit gas to 2300
                            if(top.contains("2300_"))
                                gasLimited = true;
                            else if(Utils.getType(top) == Utils.DIGITAL){
                                Long limitNum = Long.valueOf(top.split("_")[0]);
                                if(limitNum <= 2300)
                                    gasLimited = true;
                            }

                            else{
                                callPC = pc;
                                gasLimited = false; //false is the default value
                            }
                        }

                        String transfer_amount = tmp[2].split(" ")[2];
                        if(Utils.getType(transfer_amount) == Utils.DIGITAL){
                            Long amount = Long.valueOf(transfer_amount.split("_")[0]);
                            if(amount == 0)
                                legalTransferAmount = false;
                            else
                                legalTransferAmount = true;
                        }
                        else{
                            legalTransferAmount = true;
                        }
                    }
                }

            }


            if(!gasLimited && (sstorePC == -1 || sstorePC > callPC) && (callPC > sloadPC) && legalTransferAmount){
                return true;
            }
        }

        return false;
    }


    public String printAllDetectResult(){
        String res = "";
        res += "Uncheck External Calls: " + this.hasUnchechedExternalCalls + "\n";
        res += "Strict Balance Equality: " + this.hasStrictBalanceEquality + "\n";
        res += "Transaction State Dependency: " + this.hasTransactionStateDependency + "\n";
        res += "Block Info Dependency: " + this.hasBlockInfoDependency + "\n";
        res += "Greedy Contract: " + this.hasGreedyContract + "\n";
        res += "DoS Under External Influence: " + this.hasDoSUnderExternalInfluence + "\n";
        res += "Nest Call: " + this.hasNestCall + "\n";
        res += "Reentrancy: " + this.hasReentrancy + "\n";
        res += "Code Coverage:" + this.binaryAnalyzer.codeCoverage + "\n";
        res += "Miss recognized Jump: " + this.binaryAnalyzer.misRecognizedJump + "\n";
        res += "Cyclomatic Complexity: " + this.binaryAnalyzer.cyclomatic_complexity + "\n";
        res += "Number of Instructions: " + this.binaryAnalyzer.numInster + "\n";
        return res;
    }
}
