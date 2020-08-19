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
        this.hasReentrancy = detectReentrancy();
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
        String res = "Reentrancy: " + this.hasReentrancy + "\n";;
        return res;
    }
}
