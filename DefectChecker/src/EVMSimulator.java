import sun.rmi.runtime.Log;

import java.util.*;

public class EVMSimulator {

    private static final int LOOP_LIMITED = 3;
    private HashMap<String, Integer> edgeVisTimes = null;
    public TreeMap<Integer, BasicBlock> pos2BlockMap = null;
    public ArrayList<String> stackEvents = new ArrayList<>();
    public boolean versionGap = false;
    public boolean misRecognizedJump = false;

    public EVMSimulator(TreeMap<Integer, BasicBlock> pos2BlockMap){
        this.pos2BlockMap = pos2BlockMap;
        this.edgeVisTimes = new HashMap<>();
        BasicBlock start = pos2BlockMap.get(0);
        dfs_exe_block(start, (LinkedList<String>) start.evm_stack.clone());
    }

    private boolean flagVisEdge(int currentBlockID, int jumpPos){
        String edgeVis = currentBlockID + "_" + jumpPos;
        int visTimes = 0;
        if(edgeVisTimes.containsKey(edgeVis)){
            visTimes =edgeVisTimes.get(edgeVis);
        }

        if(visTimes == LOOP_LIMITED ) {
            return false;
        }

        visTimes += 1;
        edgeVisTimes.put(edgeVis, visTimes);
        return true;
    }

    private void dfs_exe_block(BasicBlock block, LinkedList<String> father_evm_stack){
        block.evm_stack = (LinkedList<String>)father_evm_stack.clone();
        for(Pair<Integer, Pair<String, String>> instr : block.instrList){
            exe_instr(block, instr, block.evm_stack);
        }

        if(block.jumpType == BasicBlock.CONDITIONAL){
            int left_branch = block.conditionalJumpPos;
            if(left_branch == -1)
                return;
            if(flagVisEdge(block.startBlockPos, left_branch)) {
                dfs_exe_block(pos2BlockMap.get(left_branch), block.evm_stack);
            }

            int right_branch = block.fallPos;
            if(flagVisEdge(block.startBlockPos, right_branch)) {
                dfs_exe_block(pos2BlockMap.get(right_branch), block.evm_stack);
            }

        }

        else if(block.jumpType == BasicBlock.UNCONDITIONAL){
            int jumpPos = block.unconditionalJumpPos;
            if(jumpPos == -1)
                return;
            if(flagVisEdge(block.startBlockPos, jumpPos)) {
                dfs_exe_block(pos2BlockMap.get(jumpPos), block.evm_stack);
            }
        }

        else if(block.jumpType == BasicBlock.FALL){
            int jumpPos = block.fallPos;
            if(flagVisEdge(block.startBlockPos, jumpPos)) {
                dfs_exe_block(pos2BlockMap.get(jumpPos), block.evm_stack);
            }

        }

        else if(block.jumpType == BasicBlock.TERMINAL){
            ;
        }

    }


    private String printStack(String instr, int current_PC, LinkedList<String> evm_stack){
        String res = "";
        for(String tmp : evm_stack){
            res += tmp + " ";
        }
        if(res.length() == 0)
            res = "NULL";
        return instr + " ==> " + current_PC + " ==> " +res;
    }

    private void exe_instr(BasicBlock currentBlock, Pair<Integer, Pair<String, String>> instr_pair, LinkedList<String> evm_stack){
        int currentBlockID = currentBlock.startBlockPos;
        String instr = instr_pair.getSecond().getFirst();
        int current_PC = instr_pair.getFirst();

        boolean legalInstr = true;
        //each instr is pushed into evm_stack with the format "instrName_currentPC"
        if(instr.equals("JUMP")){
            if(evm_stack.size() >= 1) {
                String address = evm_stack.pop();
                boolean legalJump = false;
                if(Utils.getType(address) == Utils.DIGITAL){
                    int jumpPos = Integer.valueOf(address.split("_")[0]);
                    if(jumpPos == 0 || !this.pos2BlockMap.containsKey(jumpPos)){
                        this.pos2BlockMap.get(currentBlockID).unconditionalJumpPos = -1;
                        legalJump = true;
                        versionGap = true;
                    }
                    else if(this.pos2BlockMap.get(jumpPos).instrList.get(0).getSecond().getFirst().equals("JUMPDEST")) {
                        this.pos2BlockMap.get(currentBlockID).unconditionalJumpPos = jumpPos;
                        legalJump = true;
                    }
                }
                if(!legalJump){
                    System.out.println("Cannot Recognize Jump Pos \"" + address + "\" on PC: " + current_PC);
                    this.misRecognizedJump = true;
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("JUMPI")){
            if(evm_stack.size() >= 2){
                String address = evm_stack.pop();
                String condition = evm_stack.pop();
                boolean legalJump = false;
                if(Utils.getType(address) == Utils.DIGITAL){
                    int jumpPos = Integer.valueOf(address.split("_")[0]);
                    if(jumpPos == 0 || !this.pos2BlockMap.containsKey(jumpPos)){
                        this.pos2BlockMap.get(currentBlockID).conditionalJumpPos = -1;
                        this.pos2BlockMap.get(currentBlockID).conditionalJumpExpression = condition;
                        legalJump = true;
                        versionGap = true;

                    }

                    else if(this.pos2BlockMap.get(jumpPos).instrList.get(0).getSecond().getFirst().equals("JUMPDEST")){
                        this.pos2BlockMap.get(currentBlockID).conditionalJumpPos = jumpPos;
                        this.pos2BlockMap.get(currentBlockID).conditionalJumpExpression = condition;
                        legalJump = true;
                    }

                }
                if(!legalJump){
                    System.out.println("Error JUMPI on: " + current_PC);
                    this.misRecognizedJump = true;
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("STOP")){
            ;
        }

        else if(instr.equals("ADD")){
            if(evm_stack.size() >= 2) {
                String first = evm_stack.pop();
                String second = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL && Utils.getType(second) == Utils.DIGITAL){
                    Long res = Long.valueOf(first.split("_")[0]) + Long.valueOf(second.split("_")[0]);
                    evm_stack.push(res + "_" + current_PC);
                }
                else{
                    String res = "ADD_" + current_PC + "(" + first + "," + second + ")";
                    evm_stack.push(res);
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("MUL")){
            if(evm_stack.size() >= 2) {
                String first = evm_stack.pop();
                String second = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL && Utils.getType(second) == Utils.DIGITAL){
                    Long res = Long.valueOf(first.split("_")[0]) * Long.valueOf(second.split("_")[0]);
                    evm_stack.push(res + "_" + current_PC);
                }
                else{
                    String res = "MUL_" + current_PC + "(" + first + "," + second + ")";
                    evm_stack.push(res);
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("SUB")){
            if(evm_stack.size() >= 2) {
                String first = evm_stack.pop();
                String second = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL && Utils.getType(second) == Utils.DIGITAL){
                    Long res = Long.valueOf(first.split("_")[0]) - Long.valueOf(second.split("_")[0]);
                    evm_stack.push(res + "_" + current_PC);
                }
                else{
                    String res = "SUB_" + current_PC + "(" + first + "," + second + ")";
                    evm_stack.push(res);
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("DIV")){
            if(evm_stack.size() >= 2) {
                String first = evm_stack.pop();
                String second = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL && Utils.getType(second) == Utils.DIGITAL){
                    Long res = -1L;
                    if(Long.valueOf(second.split("_")[0]) == 0)
                        res = 0L;
                    else{
                        res = Long.valueOf(first.split("_")[0]) / Long.valueOf(second.split("_")[0]);
                    }
                    evm_stack.push(res + "_" + current_PC);
                }
                else{
                    String res = "DIV_" + current_PC + "(" + first + "," + second + ")";
                    evm_stack.push(res);
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("SDIV")){
            if(evm_stack.size() >= 2) {
                String first = evm_stack.pop();
                String second = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL && Utils.getType(second) == Utils.DIGITAL){
                    Long res = -1L;
                    if(Long.valueOf(second.split("_")[0]) == 0)
                        res = 0L;
                    else{
                        int tmp = 1;
                        if(Long.valueOf(first.split("_")[0]) / Long.valueOf(second.split("_")[0]) < 0)
                            tmp = -1;
                        res = tmp * Math.abs(Long.valueOf(first.split("_")[0]) / Long.valueOf(second.split("_")[0]));
                    }
                    evm_stack.push(res + "_" + current_PC);
                }
//                else if(second.contains("-ffffffffffffffffffff")){
//                    evm_stack.push("-ffffffffffffffffffff" + "_" + current_PC);
//                }
                else{
                    String res = "SDIV_" + current_PC + "(" + first + "," + second + ")";
                    evm_stack.push(res);
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("MOD")){
            if(evm_stack.size() >= 2) {
                String first = evm_stack.pop();
                String second = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL && Utils.getType(second) == Utils.DIGITAL){
                    Long res = -1L;
                    if(Integer.valueOf(second.split("_")[0]) == 0)
                        res = 0L;
                    else{
                        res = Long.valueOf(first.split("_")[0]) % Long.valueOf(second.split("_")[0]);
                    }
                    evm_stack.push(res + "_" + current_PC);
                }
                else{
                    String res = "MOD_" + current_PC + "(" + first + "," + second + ")";
                    evm_stack.push(res);
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("SMOD")){
            if(evm_stack.size() >= 2) {
                String first = evm_stack.pop();
                String second = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL && Utils.getType(second) == Utils.DIGITAL){
                    Long res = -1L;
                    if(Integer.valueOf(second.split("_")[0]) == 0)
                        res = 0L;
                    else{
                        res = Long.valueOf(first.split("_")[0]) % Long.valueOf(second.split("_")[0]);
                        if(res * Integer.valueOf(first.split("_")[0]) < 0)
                            res *= -1;
                    }
                    evm_stack.push(res + "_" + current_PC);
                }
                else{
                    String res = "SMOD_" + current_PC + "(" + first + "," + second + ")";
                    evm_stack.push(res);
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("ADDMOD")){
            if(evm_stack.size() >= 3) {
                String first = evm_stack.pop();
                String second = evm_stack.pop();
                String third = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL && Utils.getType(second) == Utils.DIGITAL && Utils.getType(third) == Utils.DIGITAL){
                    Long res = -1L;
                    if(Integer.valueOf(third.split("_")[0]) == 0)
                        res = 0L;
                    else{
                        res = (Long.valueOf(first.split("_")[0]) + Long.valueOf(second.split("_")[0]))
                                    % Long.valueOf(third.split("_")[0]);
                    }
                    evm_stack.push(res + "_" + current_PC);
                }
                else{
                    String res = "ADDMOD_" + current_PC + "(" + first + "," + second + ")";
                    evm_stack.push(res);
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("MULMOD")){
            if(evm_stack.size() >= 3) {
                String first = evm_stack.pop();
                String second = evm_stack.pop();
                String third = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL && Utils.getType(second) == Utils.DIGITAL && Utils.getType(third) == Utils.DIGITAL){

                    Long res = (Long.valueOf(first.split("_")[0]) * Long.valueOf(second.split("_")[0]))
                                % Long.valueOf(third.split("_")[0]);

                    evm_stack.push(res + "_" + current_PC);
                }
                else{
                    String res = "MULMOD_" + current_PC + "(" + first + "," + second + ")";
                    evm_stack.push(res);
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("EXP")){
            if(evm_stack.size() >= 2) {
                String first = evm_stack.pop();
                String second = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL && Utils.getType(second) == Utils.DIGITAL){
                    Long res = new Double(Math.pow(Long.valueOf(first.split("_")[0]),
                            Long.valueOf(second.split("_")[0]))).longValue();
                    evm_stack.push( res+ "_" + current_PC);
                }
                else{
                    String res = "EXP_" + current_PC + "(" + first + "," + second + ")";
                    evm_stack.push(res);
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("SIGNEXTEND")){
            if(evm_stack.size() >= 2) {
                String first = evm_stack.pop();
                String second = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL && Utils.getType(second) == Utils.DIGITAL){
                    Long f = Long.valueOf(first.split("_")[0]);
                    Long s = Long.valueOf(first.split("_")[0]);
                    Long res = 0L;
                    if(f >= 32 || f < 0)
                        res = s;
                    else{
                        Long tmp = 8*f+7;
                        if((s & (1 << tmp)) == 1){
                            res = s | (new Double(Math.pow(2,256) - (1 << tmp)).intValue());
                        }
                        else{
                            res = s & ((1 << tmp) - 1);
                        }
                    }
                    evm_stack.push( res+ "_" + current_PC);
                }
                else{
                    String res = "SIGNEXTEND_" + current_PC + "(" + first + "," + second + ")";
                    evm_stack.push(res);
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("LT") || instr.equals("SLT")){
            if(evm_stack.size() >= 2) {
                String first = evm_stack.pop();
                String second = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL && Utils.getType(second) == Utils.DIGITAL){
                    if(Long.valueOf(first.split("_")[0]) < Long.valueOf(second.split("_")[0])){
                        evm_stack.push( 1+ "_" + current_PC);
                    }
                    else{
                        evm_stack.push( 0+ "_" + current_PC);
                    }
                }
                else{
                    String res = "LT_" + current_PC + "(" + first + "," + second + ")";
                    evm_stack.push(res);
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("GT") || instr.equals("SGT")){
            if(evm_stack.size() >= 2) {
                String first = evm_stack.pop();
                String second = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL && Utils.getType(second) == Utils.DIGITAL){
                    if(Long.valueOf(first.split("_")[0]) > Long.valueOf(second.split("_")[0])){
                        evm_stack.push( 1+ "_" + current_PC);
                    }
                    else{
                        evm_stack.push( 0+ "_" + current_PC);
                    }
                }
                else{
                    String res = "GT_" + current_PC + "(" + first + "," + second + ")";
                    evm_stack.push(res);
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("EQ")){
            if(evm_stack.size() >= 2) {
                String first = evm_stack.pop();
                String second = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL && Utils.getType(second) == Utils.DIGITAL){
                    if(Long.valueOf(first.split("_")[0]).equals(Long.valueOf(second.split("_")[0]))){
                        evm_stack.push( 1+ "_" + current_PC);
                    }
                    else{
                        evm_stack.push( 0+ "_" + current_PC);
                    }
                }
                else{
                    if(first.replaceAll("_[0-9]+?", "").equals(second.replaceAll("_[0-9]+?", ""))){
                        evm_stack.push( 1+ "_" + current_PC);
                    }
                    else{
                        String res = "EQ_" + current_PC + "(" + first + "," + second + ")";
                        evm_stack.push(res);
                    }
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("ISZERO")){
            if(evm_stack.size() >= 1) {
                String first = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL){
                    if(Long.valueOf(first.split("_")[0]) == 0){
                        evm_stack.push( 1+ "_" + current_PC);
                    }
                    else{
                        evm_stack.push( 0+ "_" + current_PC);
                    }
                }
                else{
                    String res = "ISZERO_" + current_PC + "(" + first + ")";
                    evm_stack.push(res);
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("AND")){
            if(evm_stack.size() >= 2) {
                String first = evm_stack.pop();
                String second = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL && Utils.getType(second) == Utils.DIGITAL){
                    Long res = Long.valueOf(first.split("_")[0]) & Long.valueOf(second.split("_")[0]);
                    evm_stack.push(res + "_" + current_PC);
                }
                else{
                    String res = "AND_" + current_PC + "(" + first + "," + second + ")";
                    evm_stack.push(res);
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("OR")){
            if(evm_stack.size() >= 2) {
                String first = evm_stack.pop();
                String second = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL && Utils.getType(second) == Utils.DIGITAL){
                    Long res = Long.valueOf(first.split("_")[0]) | Long.valueOf(second.split("_")[0]);
                    evm_stack.push(res + "_" + current_PC);
                }
                else{
                    String res = "OR_" + current_PC + "(" + first + "," + second + ")";
                    evm_stack.push(res);
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("XOR")){
            if(evm_stack.size() >= 2) {
                String first = evm_stack.pop();
                String second = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL && Utils.getType(second) == Utils.DIGITAL){
                    Long res = Long.valueOf(first.split("_")[0]) ^ Long.valueOf(second.split("_")[0]);
                    evm_stack.push(res + "_" + current_PC);
                }
                else{
                    String res = "XOR_" + current_PC + "(" + first + "," + second + ")";
                    evm_stack.push(res);
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("NOT")){
            if(evm_stack.size() >= 1) {
                String first = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL){
                    Long res = ~Long.valueOf(first.split("_")[0]);
                    evm_stack.push(res + "_" + current_PC);
                }
                else{
                    String res = "NOT_" + current_PC + "(" + first + ")";
                    evm_stack.push(res);
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("BYTE")){
            if(evm_stack.size() >= 2) {
                String first = evm_stack.pop();
                String second = evm_stack.pop();
                if(Utils.getType(first) == Utils.DIGITAL && Utils.getType(second) == Utils.DIGITAL){
                    Long f = Long.valueOf(first.split("_")[0]);
                    Long s = Long.valueOf(second.split("_")[0]);
                    Long byte_idx = 32 - f -1;
                    Long res = -1L;
                    if(f >= 32 || f < 0){
                        res = 0L;
                    }
                    else{
                        res = s & (255 << (8 * byte_idx));
                        res = res >> (8 * byte_idx);
                    }
                    evm_stack.push(res + "_" + current_PC);
                }
                else{
                    String res = "BYTE_" + current_PC + "(" + first + "," + second + ")";
                    evm_stack.push(res);
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("SHA3")){
            if(evm_stack.size() >= 2) {
                String top1 = evm_stack.pop();
                String top2 = evm_stack.pop();
                String result = "SHA3_" + current_PC + "(" + top1 + "," + top2 + ")";
                evm_stack.push(result);
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("ADDRESS")){
            String result = "ADDRESS_" + current_PC;
            evm_stack.push(result);
        }

        else if(instr.equals("BALANCE")){
            if(evm_stack.size() >= 1) {
                String top1 = evm_stack.pop();
                String result = "BALANCE_" + current_PC  + "(" + top1 + ")";
                evm_stack.push(result);
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("ORIGIN")){
            String result = "ORIGIN_" + current_PC;
            evm_stack.push(result);
        }

        else if(instr.equals("CALLER")){
            String result = "CALLER_" + current_PC;
            evm_stack.push(result);
        }

        else if(instr.equals("CALLVALUE")){
            String result = "CALLVALUE_" + current_PC;
            evm_stack.push(result);
        }

        else if(instr.equals("CALLDATALOAD")){
            if(evm_stack.size() >= 1) {
                String top1 = evm_stack.pop();
                String result = "CALLDATALOAD_" + current_PC  + "(" + top1 + ")";
                evm_stack.push(result);
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("CALLDATASIZE")){
            String result = "CALLDATASIZE_" + current_PC;
            evm_stack.push(result);
        }


        else if(instr.equals("CALLDATACOPY")){
            if(evm_stack.size() >= 3) {
                String top1 = evm_stack.pop();
                String top2 = evm_stack.pop();
                String top3 = evm_stack.pop();
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("CODESIZE")){
            String result = "CODESIZE_" + current_PC;
            evm_stack.push(result);
        }

        else if(instr.equals("CODECOPY")){
            if(evm_stack.size() >= 3) {
                String top1 = evm_stack.pop();
                String top2 = evm_stack.pop();
                String top3 = evm_stack.pop();
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("GASPRICE")){
            String result = "GASPRICE_" + current_PC;
            evm_stack.push(result);
        }


        else if(instr.equals("EXTCODESIZE")){
            if(evm_stack.size() >= 1) {
                String top1 = evm_stack.pop();
                String result = "EXTCODESIZE_" + current_PC + "(" + top1 + ")";
                evm_stack.push(result);
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("EXTCODECOPY")){
            if(evm_stack.size() >= 4) {
                String top1 = evm_stack.pop();
                String top2 = evm_stack.pop();
                String top3 = evm_stack.pop();
                String top4 = evm_stack.pop();
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("RETURNDATASIZE")){
            String result = "RETURNDATASIZE_" + current_PC;
            evm_stack.push(result);
        }

        else if(instr.equals("RETURNDATACOPY")){
            if(evm_stack.size() >= 3) {
                String top1 = evm_stack.pop();
                String top2 = evm_stack.pop();
                String top3 = evm_stack.pop();
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("BLOCKHASH")){
            if(evm_stack.size() >= 1) {
                String top1 = evm_stack.pop();
                String result = "BLOCKHASH_" + current_PC + "(" + top1 + ")";
                evm_stack.push(result);
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("COINBASE")){
            String result = "COINBASE_" + current_PC;
            evm_stack.push(result);
        }

        else if(instr.equals("TIMESTAMP")){
            String result = "TIMESTAMP_" + current_PC;
            evm_stack.push(result);
        }

        else if(instr.equals("NUMBER")){
            String result = "NUMBER_" + current_PC;
            evm_stack.push(result);
        }

        else if(instr.equals("DIFFICULTY")){
            String result = "DIFFICULTY_" + current_PC;
            evm_stack.push(result);
        }

        else if(instr.equals("GASLIMIT")){
            String result = "GASLIMIT_" + current_PC;
            evm_stack.push(result);
        }

        else if(instr.equals("POP")){
            if(evm_stack.size() >= 1) {
                evm_stack.pop();
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("MLOAD")){
            if(evm_stack.size() >= 1) {
                String address = evm_stack.pop();
                String result = "MLOAD_" + current_PC + "(" + address + ")";
                evm_stack.push(result);
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("MSTORE") || instr.equals("MSTORE8")){
            if(evm_stack.size() >= 2) {
                String address = evm_stack.pop();
                String value = evm_stack.pop();
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("SSTORE")){
            if(evm_stack.size() >= 2) {
                String address = evm_stack.pop();
                String value = evm_stack.pop();
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("SLOAD")){
            if(evm_stack.size() >= 1) {
                String top1 = evm_stack.pop();
                String result = "SLOAD_" + current_PC + "(" + top1 + ")";
                evm_stack.push(result);
            }
            else{
                legalInstr = false;
            }
        }


        else if(instr.equals("PC")){
            String result = "PC_" + current_PC;
            evm_stack.push(result);
        }

        else if(instr.equals("MSIZE")){
            String result = "MSIZE_" + current_PC;
            evm_stack.push(result);
        }

        else if(instr.equals("GAS")){
            String result = "GAS_" + current_PC;
            evm_stack.push(result);
        }

        else if(instr.equals("JUMPDEST")){
            ;
        }

        else if(instr.startsWith("PUSH")){
            String pushedValue = instr_pair.getSecond().getSecond();
            if(pushedValue.startsWith("0"))
                pushedValue = pushedValue.replaceFirst("0+", "");
            if(pushedValue.length() == 0)
                pushedValue = "0";
            if(pushedValue.length() <= 10) {
                Long pushvalue =  Utils.Hex2Long(pushedValue);
                evm_stack.push(pushvalue + "_" + current_PC);
            }
            else{
                evm_stack.push(instr + "_" + String.valueOf(current_PC));
            }
        }

        else if(instr.startsWith("DUP")){
            String dp = instr.replace("DUP", "");
            int dupPos = Integer.valueOf(dp);
            if(evm_stack.size() >= dupPos) {
                ArrayList<String> tmp = new ArrayList<>();
                for(int i = 0;i < dupPos;i++){
                    tmp.add(evm_stack.pop());
                }
                String dupValue = tmp.get(dupPos-1);
                for(int i = dupPos-1; i>=0; i--)
                    evm_stack.push(tmp.get(i));
                evm_stack.push(String.valueOf(dupValue));
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.startsWith("SWAP")){
            String sp = instr.replace("SWAP", "");
            int swapPos = Integer.valueOf(sp);
            if(evm_stack.size() > swapPos) {
                ArrayList<String> tmp = new ArrayList<>();
                for(int i = 0;i < swapPos+1;i++){
                    tmp.add(evm_stack.pop());
                }
                evm_stack.push(tmp.get(0));
                for(int i = tmp.size()-2; i > 0; i--)
                    evm_stack.push(tmp.get(i));
                evm_stack.push(tmp.get(tmp.size()-1));
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.startsWith("LOG")){
            String lp = instr.replace("LOG", "");
            int LogPos = Integer.valueOf(lp);
            if(evm_stack.size() >= LogPos+2) {
                for(int i = 0; i < LogPos+2; i++)
                    evm_stack.pop();
            }
            else{
                legalInstr = false;
            }

        }

        else if(instr.equals("CREATE")){
            if(evm_stack.size() >= 3) {
                String top1 = evm_stack.pop();
                String top2 = evm_stack.pop();
                String top3 = evm_stack.pop();
                String result = "CREATE_" + current_PC;
                evm_stack.push(result);
            }
            else{
                legalInstr = false;
            }
        }


        else if(instr.equals("CALL")){
            if(evm_stack.size() >= 7) {
                String outgas = evm_stack.pop();
                String recipient = evm_stack.pop();
                String transfer_amount = evm_stack.pop();
                String start_data_input = evm_stack.pop();
                String size_data_input = evm_stack.pop();
                String start_data_output = evm_stack.pop();
                String size_data_ouput = evm_stack.pop();
                String result = "CALL_" + current_PC;
                evm_stack.push(result);
                currentBlock.moneyCall = true;
                if(Utils.getType(transfer_amount) == Utils.DIGITAL){
                    Long amount = Long.valueOf(transfer_amount.split("_")[0]);
                    if(amount == 0)
                        currentBlock.moneyCall = false;
                }
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("CALLCODE")){
            if(evm_stack.size() >= 7) {
                for(int i = 0;i < 7;i++)
                    evm_stack.pop();
                String result = "CALLCODE_" + current_PC;
                evm_stack.push(result);
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("RETURN") || instr.equals("REVERT")){
            if(evm_stack.size() >= 2) {
                evm_stack.pop();
                evm_stack.pop();
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("DELEGATECALL")){
            if(evm_stack.size() >= 6) {
                for(int i = 0;i < 6;i++)
                    evm_stack.pop();
                String result = "DELEGATECALL_" + current_PC;
                evm_stack.push(result);
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("STATICCALL")){
            if(evm_stack.size() >= 6) {
                for(int i = 0;i < 6;i++)
                    evm_stack.pop();
                String result = "STATICCALL_" + current_PC;
                evm_stack.push(result);
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("SELFDESTRUCT") || instr.equals("REVERT")){
            if(evm_stack.size() >= 1) {
                evm_stack.pop();
                return;
            }
            else{
                legalInstr = false;
            }
        }

        else if(instr.equals("INVALID") || instr.equals("ASSERTFAIL")){
            ;
        }

        else{

            System.out.println("Cannot recognize instr: " + instr);
        }

        if(!legalInstr){
            System.out.println("Error with instr: " + instr + " - " + String.valueOf(current_PC));
        }
        stackEvents.add( printStack(instr, current_PC, evm_stack));
//        System.out.println(printStack(instr, current_PC, evm_stack));
    }

}
