import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Main {

    public static DefectChecker parserFromBytecode(String bytecode){
        if(bytecode.length() < 1)
            return null;
        bytecode = bytecode.replaceAll("a165627a7a72305820\\S{64}0029$", ""); //remove swarm hash
        //we only need runtime bytecode, remove creation bytecode
        if(bytecode.contains("f30060806040")){
            bytecode = bytecode.substring(bytecode.indexOf("f30060806040")+4);
        }

        BinaryAnalyzer binaryAnalyzer = new BinaryAnalyzer(bytecode);
        if(binaryAnalyzer.legalContract){
            try{
                DefectChecker defectChecker = new DefectChecker(binaryAnalyzer);
                defectChecker.detectAllSmells();
                return defectChecker;
            }catch (Exception e){
                e.printStackTrace();
                return null;
            }


        }
        return null;
    }


    public static void parserFromSourceCodeFile(String filePath, String mainContracts){
        String binary = Utils.runCMDWithTimeout(new String[]{"solc-0.4.25", "--bin-runtime", filePath});
        if(binary == null || binary.length() < 1) {
            System.out.println("Compile Error: " + filePath);
        }

        String[] tmp = binary.split("\n");
        boolean hasUnchechedExternalCalls = false;
        boolean hasStrictBalanceEquality = false;
        boolean hasTransactionStateDependency = false;
        boolean hasBlockInfoDependency = false;
        boolean hasDoSUnderExternalInfluence = false;
        boolean hasNestCall = false;
        boolean hasReentrancy = false;
        boolean hasGreedyContract = false;
        StringBuilder sb = new StringBuilder();
        for(int i = 0;i < tmp.length-1;i++){
            if(tmp[i].startsWith("Binary")){
                String address = tmp[i-1].replaceAll("=", "").replaceAll(" ", "").replace("\n", "");
                String bytecode = tmp[i+1];
                if(!address.contains(mainContracts))
                    continue;
                System.out.println(address);
                DefectChecker defectChecker = parserFromBytecode(bytecode);
                if(defectChecker != null){
                    hasUnchechedExternalCalls |= defectChecker.hasUnchechedExternalCalls;
                    hasStrictBalanceEquality |= defectChecker.hasStrictBalanceEquality;
                    hasTransactionStateDependency |= defectChecker.hasTransactionStateDependency;
                    hasBlockInfoDependency |= defectChecker.hasBlockInfoDependency;
                    hasDoSUnderExternalInfluence |= defectChecker.hasDoSUnderExternalInfluence;
                    hasNestCall |= defectChecker.hasNestCall;
                    hasReentrancy |= defectChecker.hasReentrancy;
                    hasGreedyContract |= defectChecker.hasGreedyContract;
                    sb.append("Uncheck External Calls: " + hasUnchechedExternalCalls + "\n");
                    sb.append("Strict Balance Equality: " + hasStrictBalanceEquality + "\n");
                    sb.append("Transaction State Dependency: " + hasTransactionStateDependency + "\n");
                    sb.append("Block Info Dependency: " + hasBlockInfoDependency + "\n");
                    sb.append("Greedy Contract: " + hasGreedyContract + "\n");
                    sb.append("DoS Under External Influence: " + hasDoSUnderExternalInfluence + "\n");
                    sb.append("Nest Call: " + hasNestCall + "\n");
                    sb.append("Reentrancy: " + hasReentrancy + "\n");
                    System.out.println("Uncheck External Calls: " + hasUnchechedExternalCalls);
                    System.out.println("Strict Balance Equality: " + hasStrictBalanceEquality);
                    System.out.println("Transaction State Dependency: " + hasTransactionStateDependency);
                    System.out.println("Block Info Dependency: " + hasBlockInfoDependency);
                    System.out.println("Greedy Contract: " + hasGreedyContract);
                    System.out.println("DoS Under External Influence: " + hasDoSUnderExternalInfluence);
                    System.out.println("Nest Call: " + hasNestCall);
                    System.out.println("Reentrancy: " + hasReentrancy);
                }

            }
        }



    }


    public static void main(String[] args) throws Exception{
        long startTime = System.currentTimeMillis();
        /*****Detect From Bytecode*****/
        String bytecode = "60806040526004361061004c576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff1680630103c92b146100515780630fdb1c10146100a8575b600080fd5b34801561005d57600080fd5b50610092600480360381019080803573ffffffffffffffffffffffffffffffffffffffff1690602001909291905050506100bf565b6040518082815260200191505060405180910390f35b3480156100b457600080fd5b506100bd6100d7565b005b60006020528060005260406000206000915090505481565b60008060003373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020016000205490506000811115610195573373ffffffffffffffffffffffffffffffffffffffff168160405160006040518083038185875af1925050505060008060003373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff168152602001908152602001600020819055505b505600a165627a7a72305820a2a872c11cc01048a3e18a4a62a294aaec42c25f66692b299e285dea78c55d790029";
        DefectChecker byteDefectChecker = parserFromBytecode(bytecode);
        System.out.println(byteDefectChecker.printAllDetectResult());

        /*****Detect From Source Code*****/
        String filePath = "./test.sol";
        parserFromSourceCodeFile(filePath, "Victim");


        long endTime = System.currentTimeMillis();
        System.out.println("Running time：" + (endTime - startTime) + "ms");
    }
}
