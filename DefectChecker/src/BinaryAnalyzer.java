import java.util.*;

public class BinaryAnalyzer {

    public TreeMap<Integer, BasicBlock> pos2BlockMap = new TreeMap<>();
    public ArrayList<Integer> publicFunctionStartList = new ArrayList<>(); //all the functions that can be called by others. Fallback function is not included
    public ArrayList<String> stackEvents = new ArrayList<>();
    public HashSet<String> allInstrs = new HashSet<>();
    public ArrayList<Integer> startPosList = new ArrayList<>();
    public ArrayList<Pair<Integer, Pair<String, String>>> disasm = null;
    public String disasmCode = null;
    public boolean versionGap = false;
    public boolean legalContract = true;
    public double codeCoverage = 0;
    public Integer fallbackPos = -1;
    public boolean misRecognizedJump = false;
    public int cyclomatic_complexity = 0;
    public int numInster = 0;
    private HashSet<Integer> visitBlock = null;
    private HashSet<String> totalEdge = null;

    public ArrayList<ArrayList<Integer>> allCallPath = null;
    public HashMap<String, Integer> allCirclePath2StartPos = null;

    private ArrayList<ArrayList<Integer>> allCirclePath = null;
    public BinaryAnalyzer(String bytecode){
        if(init(bytecode)){
            getBasicBlock();
            addFallEdges();
            buildCFG();
            detectBlockFeatures();
        }
    }

    private boolean init(String bytecode){
        String disasmCode = Utils.getDisasmCode(bytecode);
        if(disasmCode == null || disasmCode.length() < 1 || !disasmCode.contains("STOP")){
            legalContract = false;
            System.out.println("Disasm Failed");
            return false;
        }
//        System.out.println(disasmCode);
        this.disasmCode = disasmCode;
        this.disasm = Utils.disasmParser(disasmCode);
        this.allCallPath = new ArrayList<>();
        this.allCirclePath2StartPos = new HashMap<>();
        this.allCirclePath = new ArrayList<>();
        this.visitBlock = new HashSet();
        this.totalEdge = new HashSet<>();
        return true;
    }

    private void getBasicBlock() {
        BasicBlock block = null;
        boolean start = true;
        int lastPos = -1;
        for (int i = 0; i < this.disasm.size(); i++) {
            Pair<Integer, Pair<String, String>> instr_pair = disasm.get(i);
            int pos = instr_pair.getFirst();
            String instr = instr_pair.getSecond().getFirst();
            allInstrs.add(instr);
            if (start || instr.equals("JUMPDEST")) {
                if (i != 0) {
                    block.endBlockPos = lastPos;
                    this.pos2BlockMap.put(block.startBlockPos, block);
                }
                block = new BasicBlock();
                block.startBlockPos = pos;
                this.startPosList.add(pos);
                start = false;
            }

            else if (instr.equals("JUMPI")) {
                start = true;
                block.jumpType = BasicBlock.CONDITIONAL;

            }

            else if (instr.equals("JUMP")) {
                start = true;
                block.jumpType = BasicBlock.UNCONDITIONAL;
            }

            //some blocks may only have one instr, so we use if rathar than else if
            if (instr.equals("STOP") || instr.equals("RETURN") || instr.equals("REVERT")
                    || instr.equals("SELFDESTRUCT") || instr.equals("ASSERTFAIL")) {
                start = true;
                block.jumpType = BasicBlock.TERMINAL;
            }

            block.instrList.add(instr_pair);
            block.instrString.append(instr).append(" ");

            lastPos = pos;
            if(i == this.disasm.size() - 1){
                block.endBlockPos = lastPos;
                this.pos2BlockMap.put(block.startBlockPos, block);
            }
        }
    }

    private void addFallEdges(){
        //Edges of fall to
        for(int i = 0; i < this.startPosList.size()-1; i++){
            int startPos = startPosList.get(i);
            BasicBlock block = this.pos2BlockMap.get(startPos);
            if(block.jumpType == BasicBlock.FALL || block.jumpType == BasicBlock.CONDITIONAL){
                block.fallPos = startPosList.get(i+1);
            }
        }
    }

    private void buildCFG(){
        EVMSimulator simulator = new EVMSimulator(this.pos2BlockMap);
        this.pos2BlockMap = simulator.pos2BlockMap;
        this.stackEvents = simulator.stackEvents;
        this.versionGap = simulator.versionGap;
        this.misRecognizedJump = simulator.misRecognizedJump;
        if(versionGap){
            System.out.println("Bytecode version may not support. The default Solidity version is 0.4.25; ");
        }
    }

    private void flagLoop(ArrayList<Integer> totalPath, int startLoopPos){

        BasicBlock startLoopBlock = this.pos2BlockMap.get(startLoopPos);
        startLoopBlock.isCircleStart = true;
        boolean start = false;
        ArrayList<Integer> circlePath = new ArrayList<>();
        for(int i = 0;i < totalPath.size(); i++){
            int pos = totalPath.get(i);
            if(pos == startLoopPos)
                start = true;
            if(start){
                BasicBlock block = this.pos2BlockMap.get(pos);
                block.isCircle = true;
                circlePath.add(block.startBlockPos);
                if(i < totalPath.size() - 1){
                    String path = String.valueOf(pos) + "_" + String.valueOf(totalPath.get(i+1));
                    this.allCirclePath2StartPos.put(path, startLoopPos);
                }
                else if(i == totalPath.size()-1){
                    String path = String.valueOf(pos) + "_" + String.valueOf(startLoopPos);
                    this.allCirclePath2StartPos.put(path, startLoopPos);
                }
            }
        }
        this.allCirclePath.add(circlePath);
    }

    private void printPath(ArrayList<Integer> currentPath){
        String path = "";
        for(Integer pos : currentPath)
            path += String.valueOf(pos) + " ";
        System.out.println("Length: " + currentPath.size() + " ==> " + path);

    }
    private void findCallPathAndLoops(ArrayList<Integer> currentPath, BasicBlock block, HashSet<String> visited){
//        printPath(currentPath);
        this.visitBlock.add(block.startBlockPos);
        if(block.instrString.toString().contains("CALL ")){
            this.allCallPath.add(currentPath);
        }

        if(block.jumpType == BasicBlock.CONDITIONAL){
            int left_branch = block.conditionalJumpPos;
            String path = String.valueOf(block.startBlockPos) + "_" + String.valueOf(left_branch);
            this.totalEdge.add(path);

            if(!visited.contains(path) && left_branch > 0) {
                visited.add(path);
                ArrayList<Integer> newPath = (ArrayList<Integer>) currentPath.clone();
                newPath.add(left_branch);

                findCallPathAndLoops(newPath, this.pos2BlockMap.get(left_branch), visited);
            }

            else if(this.allCirclePath2StartPos.containsKey(path)){
                flagLoop(currentPath, this.allCirclePath2StartPos.get(path));
            }

            else if(currentPath.contains(left_branch) && left_branch > 0){
                flagLoop(currentPath, block.startBlockPos);
            }


            int right_branch = block.fallPos;
            path = String.valueOf(block.startBlockPos) + "_" + String.valueOf(right_branch);
            this.totalEdge.add(path);
            if(!visited.contains(path) && right_branch > 0) {
                visited.add(path);
                ArrayList<Integer> newPath = (ArrayList<Integer>) currentPath.clone();
                newPath.add(right_branch);
                findCallPathAndLoops(newPath, this.pos2BlockMap.get(right_branch), visited);
            }
            else if(this.allCirclePath2StartPos.containsKey(path)){
                flagLoop(currentPath, this.allCirclePath2StartPos.get(path));
            }
            else if(currentPath.contains(right_branch) && right_branch > 0){
                flagLoop(currentPath, block.startBlockPos);
            }

        }

        else if(block.jumpType == BasicBlock.UNCONDITIONAL){
            int jumpPos = block.unconditionalJumpPos;
            String path = String.valueOf(block.startBlockPos) + "_" + String.valueOf(jumpPos);
            this.totalEdge.add(path);
            if(!visited.contains(path) && jumpPos > 0) {
                visited.add(path);
                ArrayList<Integer> newPath = (ArrayList<Integer>) currentPath.clone();
                newPath.add(jumpPos);
                findCallPathAndLoops(newPath, this.pos2BlockMap.get(jumpPos), visited);
            }
            else if(this.allCirclePath2StartPos.containsKey(path)){
                flagLoop(currentPath, this.allCirclePath2StartPos.get(path));
            }
            else if(currentPath.contains(jumpPos) && jumpPos > 0){
                flagLoop(currentPath, block.startBlockPos);
            }

        }

        else if(block.jumpType == BasicBlock.FALL){
            int jumpPos = block.fallPos;
            String path = String.valueOf(block.startBlockPos) + "_" + String.valueOf(jumpPos);
            this.totalEdge.add(path);
            if(!visited.contains(path)) {
                visited.add(path);
                ArrayList<Integer> newPath = (ArrayList<Integer>) currentPath.clone();
                newPath.add(jumpPos);
                findCallPathAndLoops(newPath, this.pos2BlockMap.get(jumpPos), visited);
            }

        }



    }


    private void detectBlockFeatures(){
        BasicBlock block = this.pos2BlockMap.get(0);

        //Detect public functions' positions
        while(block.fallPos != -1){
            if(block.instrList.get(0).getSecond().getFirst().equals("JUMPDEST"))
                break;
            if(block.conditionalJumpPos != -1 && block.conditionalJumpExpression.startsWith("EQ")){
                publicFunctionStartList.add(block.conditionalJumpPos);
            }
            else
                this.fallbackPos = block.conditionalJumpPos;
            block = this.pos2BlockMap.get(block.fallPos);
        }
        if(this.pos2BlockMap.size() <= 4 && this.pos2BlockMap.get(0).instrString.toString().contains("CALLVALUE"))
            this.fallbackPos = 0;

        //Detect falback function's position
        //fallback function's position == this.pos2BlockMap.get(0).conditionalJumpPos;


        findCallPathAndLoops(new ArrayList<>(), this.pos2BlockMap.get(0), new HashSet<>());

        int visitedInstr = 0;
        int totalInstr = 0;
        for(Map.Entry<Integer, BasicBlock> entry : this.pos2BlockMap.entrySet()){
            BasicBlock tmp = entry.getValue();
            int instrNum = tmp.instrList.size();
            if(this.visitBlock.contains(tmp.startBlockPos)){
                visitedInstr += instrNum;
            }
            totalInstr += instrNum;
        }
        this.codeCoverage = (double)visitedInstr / totalInstr;
        this.cyclomatic_complexity = this.totalEdge.size() - visitBlock.size() + 2;
        this.numInster = this.disasm.size();
//        System.out.println("Start Detecting code smells");

    }

}
