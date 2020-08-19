import java.util.ArrayList;
import java.util.LinkedList;

public class BasicBlock {
    public static final int CONDITIONAL = 1;
    public static final int UNCONDITIONAL = 2;
    public static final int FALL = 3;
    public static final int TERMINAL = 4;

    public int startBlockPos = -1;
    public int endBlockPos = -1;
    public ArrayList<Pair<Integer, Pair<String, String>>> instrList = null;
    public LinkedList<String> evm_stack = null;
    public StringBuilder instrString = null;

    public int fallPos = -1;
    public int conditionalJumpPos = -1;
    public String conditionalJumpExpression = "";
    public int unconditionalJumpPos = -1;
    public int jumpType = 3; //1: conditional 2: unconditional 3: fall 4: terminal
    public boolean isCircle = false;
    public boolean isCircleStart = false;
    public boolean moneyCall = false;

    public BasicBlock(){
        evm_stack = new LinkedList<>();
        instrList = new ArrayList<>();
        instrString = new StringBuilder();
    }
}
