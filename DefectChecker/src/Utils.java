import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    public static final int DIGITAL = 1;
    public static final int STRING = 2;

    public static String runCMDWithTimeout(String cmd[]){

        final ExecutorService exec = Executors.newFixedThreadPool(1);
        String result = null;
        Callable<String> call = new Callable<String>() {
            public String call() throws Exception {
                String result = "";
                Process pro = Runtime.getRuntime().exec(cmd);

                InputStream in = null;
                in = pro.getInputStream();

                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line = "";
                while((line = reader.readLine()) != null)
                    result += line + "\n";
                pro.destroy();
                return result;
            }
        };

        try {

            Future<String> future = exec.submit(call);
            result = future.get(1000 * 10, TimeUnit.MILLISECONDS);

        } catch (TimeoutException e){
            System.out.println(cmd[cmd.length-1] + " ===> time out");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            exec.shutdown();
        }

        return result;
    }


    public static String getDisasmCode(String binary){
        String disasmCode = "";
        if(binary.startsWith("0x"))
            binary = binary.substring(2,binary.length());
        File tmp = null;
        try{
            tmp = new File("./tmp.txt");

            BufferedWriter writer = new BufferedWriter(new FileWriter(tmp));
            writer.write(binary);
            writer.flush();
            writer.close();
            disasmCode = runCMDWithTimeout(new String[]{"evm-1.8.14" , "disasm", tmp.getAbsolutePath()});
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            tmp.delete();
        }
        return disasmCode;
    }

    public static String replaceInsr(String line){
        line = line.replace("SUICIDE", "SELFDESTRUCT");
        line = line.replace("Missing opcode 0xfd", "REVERT");
        line = line.replace("Missing opcode 0xfe", "ASSERTFAIL");
        line = line.replaceAll("Missing opcode .+?", "INVALID");

        return line;
    }

    public static int Hex2Int(String hex){
        if(hex.length() < 1)
            return 0;
        int parseInt = Integer.parseInt(hex, 16);
        return parseInt;
    }

    public static Long Hex2Long(String hex){
        if(hex.length() < 1)
            return 0L;
        Long parseLong = Long.parseLong(hex, 16);
        return parseLong;
    }

    public static ArrayList<Pair<Integer, Pair<String, String>>> disasmParser(String disasmCode){
        ArrayList<Pair<Integer, Pair<String, String>>> disasm = new ArrayList<>();
        String[] lines = disasmCode.split("\n");
        boolean start = false;
        for(String line : lines){
            if(!start){
                start = true;
                continue;
            }
            line = replaceInsr(line);
            String[] tmp = line.split(": ");
            //this is depends on evm verson
//            int lineID = Utils.Hex2Int(tmp[0]);
            int lineID = Integer.parseInt(tmp[0]);
            String[] tmp2 = tmp[1].split(" ");
            String pushID = "";
            if(tmp2.length > 1){
                pushID = tmp2[1].replaceFirst("0x", "");
            }
            Pair<String, String> instr = new Pair<String, String>();
            instr.setFirst(tmp2[0]);
            instr.setSecond(pushID);

            Pair<Integer, Pair<String, String>> lineInstr = new Pair<Integer, Pair<String, String>>();
            lineInstr.setFirst(lineID);
            lineInstr.setSecond(instr);
            disasm.add(lineInstr);
        }
        return disasm;

    }

    public static int getType(String instr){
        //1: integer 2:string Solidity doesn't have double
        int result = Utils.DIGITAL;
        for(int i = 0;i < instr.length();i++){
            if(Character.isDigit(instr.charAt(i)) || instr.charAt(i) == '_')
                continue;
            else{
                result = Utils.STRING;
                break;
            }
        }
        return result;
    }

    public static Pair<String, String> splitInstr2(String instr){
        instr = instr.replaceAll(" ", "");
        Pair<String, String> res = new Pair<>();
        //EXAMPLE1: EQ_1210(AND_1186(PUSH20_1165,AND_1164(PUSH20_1143,DIV_1142(SLOAD_1135(0_1131),1_1140))),AND_1209(PUSH20_1188,ORIGIN_1187))
        //EXAMPLE2: EQ_1210(PUSH20_1165,AND_1209(PUSH20_1188,ORIGIN_1187))
        //EXAMPLE3: AND_1209(PUSH20_1188,ORIGIN_1187)
        //EXAMPLE4: EQ_1210(AND_1186(PUSH20_1165,AND_1164(PUSH20_1143,DIV_1142(SLOAD_1135(0_1131),1_1140))),PUSH20_1165)
        int flag = 0;
        int startPos = -1;
        int splitPos = -1;
        int endPos = -1;
        for(int i = 0;i < instr.length(); i++){
            char c = instr.charAt(i);
            if(c == '('){
                flag++;
                if(flag == 1 && startPos == -1)
                    startPos = i;
            }
            else if(c == ')'){
                flag--;
                if(flag == 1 && startPos != -1 && splitPos != -1)
                    endPos = i+1;
                else if(flag == 0 && startPos != -1 && splitPos != -1)
                    endPos = i;
            }
            else if(c == ','){
                if(flag == 1 && startPos != -1 && splitPos == -1){
                    splitPos = i;
                }
            }
        }
        res.setFirst(instr.substring(startPos+1, splitPos));
        res.setSecond(instr.substring(splitPos+1, endPos));
        return res;
    }

    public static int getSlotID(String condition){
        //SLOAD_382(SHA3_381(0_379,64_378)
        //SLOAD_382(0_64)
        int slotID = -1;
        Pattern p = Pattern.compile("SLOAD_[0-9]+?\\(([0-9]+?)_");
        Matcher m = p.matcher(condition);
        if(m.find()){
            if (!(m.group(1) == null || "".equals(m.group(1)))) {
                String tmp = m.group(1);
                if(Utils.getType(tmp) == Utils.DIGITAL){
                    slotID = Integer.valueOf(tmp);
                }
            }
        }
        return slotID;
    }

    public static String getSlotAddress(String condition){
        //EXAMPLE: ISZERO_389(GT_388(SLOAD_382(SHA3_381(0_379,64_378)),0_385))
        String address = "";
        int start = -1;
        int end = -1;
        int idx = 0;
        for(int i = condition.indexOf("SLOAD_"); i < condition.length(); i++) {
            if (condition.charAt(i) == '(') {
                idx++;
                if(start == -1)
                    start = i;
            }
            if (condition.charAt(i) == ')') {
                idx--;
            }
            if (idx == 0 && start > 0) {
                end = i;
                break;
            }
        }
        if(end > start){
            address = condition.substring(start+1, end);
        }
        address = address.replaceAll("_[0-9]+", "");
        return address;
    }

    public static boolean legalRange(Integer pos, ArrayList<Pair<Integer, Integer>> ranges){
        for(Pair<Integer, Integer> range : ranges){
            if(pos >= range.getFirst() && pos <= range.getSecond())
                return true;
        }

        return false;
    }


    public static HashMap<String, ArrayList<Boolean>> getGroundTruth(File file){

        HashMap<String, ArrayList<Boolean>> groundTruthList = new HashMap<>();
        try{
            String line = "";
            BufferedReader reader = new BufferedReader(new FileReader(file));
            while((line = reader.readLine()) != null){
                String[] tmp = line.split(",");
                ArrayList<Boolean> answerList = new ArrayList<>();
                for(int i =0; i < 8; i++)
                    answerList.add(false);
                if(tmp[1].equals("1"))          //1-B: Uncheck External Calls
                    answerList.set(0, true);
                if(tmp[3].equals("1"))          //3-D: Strict Balance Equality
                    answerList.set(1, true);
                if(tmp[5].equals("1"))          //5-F: Transaction State Dependency
                    answerList.set(2, true);
                if(tmp[8].equals("1"))          //8-I: Block Info Dependency
                    answerList.set(3, true);
                if(tmp[18].equals("1"))         //18-S: Greedy Contract
                    answerList.set(4, true);
                if(tmp[2].equals("1"))          //2-C: DoS Under External Influence
                    answerList.set(5, true);
                if(tmp[9].equals("1"))          //9-J: Nest Call
                    answerList.set(6, true);
                if(tmp[6].equals("1"))          //6-G: Reentrancy
                    answerList.set(7, true);
                groundTruthList.put(tmp[0].replaceFirst("0x", ""), answerList);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return groundTruthList;
    }

    public static String getBytecodeFromFile(File file){
        String bytecode = "";
        try{

            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line = "";
            while((line = reader.readLine()) != null){
                bytecode += line;
            }
        }catch (Exception e){
            e.printStackTrace();
        }

        return bytecode;
    }

    public static void printAnswerCheck(ArrayList<Boolean> groundTruth, DefectChecker defectChecker){
        //0. Uncheck External Calls 1. Strict Balance Equality 2. Transaction State Dependency 3. Block Info Dependency
        //4. Greedy Contract 5. DoS Under External Influence 6. Nest Call 7. Reentrancy
        if(defectChecker.hasUnchechedExternalCalls != groundTruth.get(0)){
            System.out.println("Uncheck External Calls check fail. The answer is " + groundTruth.get(0));
        }

        if(defectChecker.hasStrictBalanceEquality != groundTruth.get(1)){
            System.out.println("Strict Balance Equality check fail. The answer is " + groundTruth.get(1));
        }

        if(defectChecker.hasTransactionStateDependency != groundTruth.get(2)){
            System.out.println("Transaction State Dependency check fail. The answer is " + groundTruth.get(2));
        }

        if(defectChecker.hasBlockInfoDependency != groundTruth.get(3)){
            System.out.println("Block Info Dependency check fail. The answer is " + groundTruth.get(3));
        }

        if(defectChecker.hasGreedyContract != groundTruth.get(4)){
            System.out.println("Greedy Contract check fail. The answer is " + groundTruth.get(4));
        }

        if(defectChecker.hasDoSUnderExternalInfluence != groundTruth.get(5)){
            System.out.println("DoS Under External Influence check fail. The answer is " + groundTruth.get(5));
        }

        if(defectChecker.hasNestCall != groundTruth.get(6)){
            System.out.println("Nest Call check fail. The answer is " + groundTruth.get(6));
        }

        if(defectChecker.hasReentrancy != groundTruth.get(7)){
            System.out.println("Reentrancy check fail. The answer is " + groundTruth.get(7));
        }

    }
}
