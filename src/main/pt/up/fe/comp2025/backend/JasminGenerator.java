package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.LiteralElement;
import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.Operand;
import org.specs.comp.ollir.Element;
import org.specs.comp.ollir.Field;
import org.specs.comp.ollir.type.Type;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 *
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;
    List<Report> reports;
    String code;
    Method currentMethod;

    // stack and locals for .limit calculation
    private int currentStackSize = 0;
    private int maxStackSize = 0;
    private int maxLocals = 0;

    private final JasminUtils types;
    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;
        reports = new ArrayList<>();
        code = null;
        currentMethod = null;
        types = new JasminUtils(ollirResult);

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(PutFieldInstruction.class, this::generatePutField);
        generators.put(GetFieldInstruction.class, this::generateGetField);
        generators.put(CondBranchInstruction.class, this::generateCondBranch);
        generators.put(GotoInstruction.class, this::generateGoto);
    }

    private String apply(TreeNode node) {
        var code = new StringBuilder();

        // Print the corresponding OLLIR code as a comment
        //code.append("; ").append(node).append(NL);

        code.append(generators.apply(node));
        return code.toString();
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = apply(ollirResult.getOllirClass());
        }
        return code;
    }

    private String generateClassUnit(ClassUnit classUnit) {
        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class ").append(className).append(NL).append(NL);

        // TODO: When you support 'extends', this must be updated
        var fullSuperClass = "java/lang/Object";
        if (classUnit.getSuperClass() != null) {
            fullSuperClass = classUnit.getSuperClass();
        }
        code.append(".super ").append(fullSuperClass).append(NL);

        // generate fields if they exist
        for (Field field : classUnit.getFields()) {
            code.append(".field ");
            code.append(types.getModifier(field.getFieldAccessModifier()));
            code.append(field.getFieldName());
            code.append(" ");
            code.append(getJasminType(field.getFieldType()));
            code.append(NL);
        }

        // generate a single constructor method
        var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial %s/<init>()V
                    return
                .end method
                """.formatted(fullSuperClass);
        code.append(defaultConstructor);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }
            code.append(apply(method));
        }

        return code.toString();
    }

    private String generateMethod(Method method) {

        //System.out.println("STARTING METHOD " + method.getMethodName());
        // set method

        currentMethod = method;
        var code = new StringBuilder();

        // reset limits tracking
        currentStackSize = 0;
        maxStackSize = 0;
        maxLocals = calculateMaxLocals(method);

        // calculate modifier
        var modifier = types.getModifier(method.getMethodAccessModifier());
        var methodName = method.getMethodName();

        // TODO: Hardcoded param types and return type, needs to be expanded
        StringBuilder signature = new StringBuilder();
        signature.append("(");

        for (Element param : method.getParams()) {
            signature.append(getJasminType(param.getType()));
        }
        signature.append(")");
        signature.append(getJasminType(method.getReturnType()));

        code.append("\n.method ").append(modifier)
                .append(methodName).append(signature).append(NL);

        // method body and max stack size calculated
        StringBuilder bodyCode = new StringBuilder();
        for (var inst : method.getInstructions()) {
            String instCode = apply(inst);
            if (!instCode.trim().isEmpty()) {
                var lines = StringLines.getLines(instCode).stream()
                        .collect(Collectors.joining(NL + TAB, TAB, NL));
                bodyCode.append(lines);
            }
        }

        // Add limits
        int finalStackLimit = Math.max(maxStackSize, 10);
        code.append(TAB).append(".limit stack ").append(finalStackLimit).append(NL);
        code.append(TAB).append(".limit locals ").append(maxLocals).append(NL);

        code.append(bodyCode);
        code.append(".end method\n");

        // unset method
        currentMethod = null;
        //System.out.println("ENDING METHOD " + method.getMethodName());
        return code.toString();
    }

    private int calculateMaxLocals(Method method) {
        int maxReg = 0;

        if (!method.isStaticMethod()) {
            maxReg = 1;
        }
        maxReg += method.getParams().size();

        // local variables
        var varTable = method.getVarTable();
        for (var entry : varTable.entrySet()) {
            int reg = entry.getValue().getVirtualReg();
            maxReg = Math.max(maxReg, reg + 1);
        }

        return Math.max(maxReg, 5);
    }

    private void updateStackSize(int change) {
        currentStackSize += change;
        maxStackSize = Math.max(maxStackSize, currentStackSize);
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();
        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

        // Check for iinc optimization (i = i + 1 pattern)
        if (assign.getRhs() instanceof BinaryOpInstruction) {
            BinaryOpInstruction binOp = (BinaryOpInstruction) assign.getRhs();
            if (binOp.getOperation().getOpType().toString().equals("ADD")) {
                Element left = binOp.getLeftOperand();
                Element right = binOp.getRightOperand();

                // Check if it's i = i + constant
                if (left instanceof Operand && right instanceof LiteralElement) {
                    String constValue = ((LiteralElement) right).getLiteral();
                    if (constValue.equals("1")) {
                        String leftVarName = ((Operand) left).getName();
                        var leftReg = currentMethod.getVarTable().get(leftVarName);

                        code = new StringBuilder(); // Clear previous code
                        code.append("iinc ").append(leftReg.getVirtualReg()).append(" 1").append(NL);
                        updateStackSize(0); // iinc doesn't use stack
                        return code.toString();
                    }
                }
            }
        }

        // Check if it's a field assignment
        if (isFieldAccess(operand)) {
            code.append("aload_0").append(NL);
            updateStackSize(1);
            code.append("swap").append(NL);
            code.append("putfield ").append(currentMethod.getOllirClass().getClassName())
                    .append("/").append(operand.getName()).append(" ")
                    .append(getJasminType(operand.getType())).append(NL);
            updateStackSize(-2);
        } else {
            // get register
            var reg = currentMethod.getVarTable().get(operand.getName());

            // TODO: Hardcoded for int type, needs to be expanded
            String storeInst = getStoreInstruction(operand.getType(), reg.getVirtualReg());
            code.append(storeInst).append(NL);
            updateStackSize(-1);
        }

        return code.toString();
    }

    private boolean isFieldAccess(Operand operand) {
        return ollirResult.getOllirClass().getFields().stream()
                .anyMatch(field -> field.getFieldName().equals(operand.getName()));
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        String litValue = literal.getLiteral();
        updateStackSize(1); // pushes one value

        try {
            int value = Integer.parseInt(litValue);
            return getConstantLoadInstruction(value);
        } catch (NumberFormatException e) {
            // boolean or other literals
            if ("true".equals(litValue)) {
                return "iconst_1" + NL;
            } else if ("false".equals(litValue)) {
                return "iconst_0" + NL;
            }
            return "ldc " + litValue + NL;
        }
    }

    private String generateOperand(Operand operand) {
        // Check if it's a field access
        if (isFieldAccess(operand)) {
            var code = new StringBuilder();
            code.append("aload_0").append(NL);
            code.append("getfield ").append(currentMethod.getOllirClass().getClassName())
                    .append("/").append(operand.getName()).append(" ")
                    .append(getJasminType(operand.getType())).append(NL);
            updateStackSize(1);
            return code.toString();
        }

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName());
        updateStackSize(1); // pushes one value

        // TODO: Hardcoded for int type, needs to be expanded
        String loadInst = getLoadInstruction(operand.getType(), reg.getVirtualReg());
        return loadInst + NL;
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // Handle comparison operations
        if (isComparisonOp(binaryOp.getOperation().getOpType())) {
            return generateComparison(binaryOp);
        }

        // load values on the left and on the right
        code.append(apply(binaryOp.getLeftOperand()));
        code.append(apply(binaryOp.getRightOperand()));

        // TODO: Hardcoded for int type, needs to be expanded
        var typePrefix = "i";

        // apply operation
        String jasminOp = getJasminOperation(binaryOp.getOperation().getOpType());
        code.append(jasminOp).append(NL);
        updateStackSize(-1);

        return code.toString();
    }

    private boolean isComparisonOp(org.specs.comp.ollir.OperationType opType) {
        return opType == org.specs.comp.ollir.OperationType.LTH ||
                opType == org.specs.comp.ollir.OperationType.GTH ||
                opType == org.specs.comp.ollir.OperationType.EQ ||
                opType == org.specs.comp.ollir.OperationType.NEQ ||
                opType == org.specs.comp.ollir.OperationType.LTE ||
                opType == org.specs.comp.ollir.OperationType.GTE;
    }

    private String generateComparison(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        code.append(apply(binaryOp.getLeftOperand()));
        code.append(apply(binaryOp.getRightOperand()));

        String trueLabel = "true_" + System.currentTimeMillis();
        String endLabel = "end_" + System.currentTimeMillis();

        // Check if right operand is zero constant for optimization
        if (binaryOp.getRightOperand() instanceof LiteralElement) {
            LiteralElement rightLit = (LiteralElement) binaryOp.getRightOperand();
            if (rightLit.getLiteral().equals("0")) {
                // Optimize: use single operand comparison against zero
                code = new StringBuilder(); // Reset code
                code.append(apply(binaryOp.getLeftOperand())); // Only load left operand

                String compareInst = switch (binaryOp.getOperation().getOpType()) {
                    case LTH -> "iflt";
                    case GTH -> "ifgt";
                    case EQ -> "ifeq";
                    case NEQ -> "ifne";
                    case LTE -> "ifle";
                    case GTE -> "ifge";
                    default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
                };

                code.append(compareInst).append(" ").append(trueLabel).append(NL);
                code.append("iconst_0").append(NL);
                code.append("goto ").append(endLabel).append(NL);
                code.append(trueLabel).append(":").append(NL);
                code.append("iconst_1").append(NL);
                code.append(endLabel).append(":").append(NL);

                updateStackSize(-1);
                return code.toString();
            }
        }

        // Fall back to two-operand comparison
        String compareInst = switch (binaryOp.getOperation().getOpType()) {
            case LTH -> "if_icmplt";
            case GTH -> "if_icmpgt";
            case EQ -> "if_icmpeq";
            case NEQ -> "if_icmpne";
            case LTE -> "if_icmple";
            case GTE -> "if_icmpge";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(compareInst).append(" ").append(trueLabel).append(NL);
        code.append("iconst_0").append(NL);
        code.append("goto ").append(endLabel).append(NL);
        code.append(trueLabel).append(":").append(NL);
        code.append("iconst_1").append(NL);
        code.append(endLabel).append(":").append(NL);

        updateStackSize(-1);
        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        if (returnInst.hasReturnValue()) {
            var operand = returnInst.getOperand();
            if (operand.isPresent()) {
                code.append(apply(operand.get()));
            }
        }

        // TODO: Hardcoded for int type, needs to be expanded
        String returnType = getReturnInstruction(currentMethod.getReturnType());
        code.append(returnType).append(NL);

        if (returnInst.hasReturnValue()) {
            updateStackSize(-1);
        }

        return code.toString();
    }

    private String generateCall(CallInstruction call) {
        var code = new StringBuilder();

        // Load arguments
        var operands = call.getOperands();
        for (int i = 1; i < operands.size(); i++) {
            code.append(apply(operands.get(i)));
        }

        // Extract clean class name
        String className = getCleanName(operands.get(0));

        // Extract clean method name - it's just an Element, not Optional
        String methodName = getCleanName(call.getMethodName());

        code.append("invokestatic ").append(className).append("/").append(methodName);
        code.append("(");
        for (int i = 1; i < operands.size(); i++) {
            code.append(getJasminType(operands.get(i).getType()));
        }
        code.append(")");
        code.append(getJasminType(call.getReturnType()));
        code.append(NL);

        updateStackSize(-(operands.size() - 1));
        if (!call.getReturnType().toString().toLowerCase().contains("void")) {
            updateStackSize(1);
        }

        return code.toString();
    }

    private String getCleanName(Element element) {
        if (element instanceof LiteralElement) {
            return ((LiteralElement) element).getLiteral().replace("\"", "");
        } else if (element instanceof Operand) {
            return ((Operand) element).getName();
        } else {
            // Fallback - try to extract just the essential part
            String str = element.toString();
            if (str.contains(":")) {
                str = str.substring(str.lastIndexOf(":") + 1).trim();
            }
            return str.replace("\"", "");
        }
    }

    private String generatePutField(PutFieldInstruction putField) {
        var code = new StringBuilder();

        code.append(apply(putField.getOperands().get(0)));
        code.append(apply(putField.getOperands().get(2)));

        String fieldName = ((Operand) putField.getOperands().get(1)).getName();

        code.append("putfield ").append(currentMethod.getOllirClass().getClassName())
                .append("/").append(fieldName).append(" ")
                .append(getJasminType(putField.getOperands().get(2).getType())).append(NL);

        updateStackSize(-2);
        return code.toString();
    }

    private String generateGetField(GetFieldInstruction getField) {
        var code = new StringBuilder();

        code.append(apply(getField.getOperands().get(0)));

        String fieldName = ((Operand) getField.getOperands().get(1)).getName();

        code.append("getfield ").append(currentMethod.getOllirClass().getClassName())
                .append("/").append(fieldName).append(" ")
                .append(getJasminType(getField.getFieldType())).append(NL);

        updateStackSize(0);
        return code.toString();
    }

    private String generateCondBranch(CondBranchInstruction condBranch) {
        var code = new StringBuilder();

        code.append(apply(condBranch.getCondition()));

        code.append("ifne ").append(condBranch.getLabel()).append(NL);
        updateStackSize(-1);

        return code.toString();
    }

    private String generateGoto(GotoInstruction gotoInst) {
        return "goto " + gotoInst.getLabel() + NL;
    }

    // helper methods

    private String getJasminType(Type type) {
        String typeName = type.toString().toLowerCase();

        if (typeName.contains("int") || typeName.contains("i32")) {
            return "I";
        } else if (typeName.contains("bool")) {
            return "Z";
        } else if (typeName.contains("void") || typeName.contains(".v")) {
            return "V";
        } else if (typeName.contains("string")) {
            return "Ljava/lang/String;";
        } else if (typeName.contains("array")) {
            if (typeName.contains("int")) {
                return "[I";
            } else if (typeName.contains("bool")) {
                return "[Z";
            }
            return "[I"; // default to int array
        } else {
            return "L" + typeName + ";";
        }
    }

    private String getLoadInstruction(Type type, int reg) {
        String baseInst;
        String typeName = type.toString().toLowerCase();

        if (typeName.contains("int") || typeName.contains("i32") || typeName.contains("bool")) {
            baseInst = "iload";
        } else {
            baseInst = "aload";
        }

        if (reg <= 3) {
            return baseInst + "_" + reg;
        }
        return baseInst + " " + reg;
    }

    private String getStoreInstruction(Type type, int reg) {
        String baseInst;
        String typeName = type.toString().toLowerCase();

        if (typeName.contains("int") || typeName.contains("i32") || typeName.contains("bool")) {
            baseInst = "istore";
        } else {
            baseInst = "astore";
        }

        if (reg <= 3) {
            return baseInst + "_" + reg;
        }
        return baseInst + " " + reg;
    }

    private String getConstantLoadInstruction(int value) {
        if (value >= -1 && value <= 5) {
            return "iconst_" + (value == -1 ? "m1" : value) + NL;
        } else if (value >= -128 && value <= 127) {
            return "bipush " + value + NL;
        } else if (value >= -32768 && value <= 32767) {
            return "sipush " + value + NL;
        } else {
            return "ldc " + value + NL;
        }
    }

    private String getJasminOperation(org.specs.comp.ollir.OperationType opType) {
        // assume integer operations for now
        return switch (opType) {
            case ADD -> "iadd";
            case SUB -> "isub";
            case MUL -> "imul";
            case DIV -> "idiv";
            case AND -> "iand";
            case OR -> "ior";
            default -> throw new NotImplementedException(opType);
        };
    }

    private String getReturnInstruction(Type returnType) {
        String typeName = returnType.toString().toLowerCase();

        if (typeName.contains("int") || typeName.contains("i32") || typeName.contains("bool")) {
            return "ireturn";
        } else if (typeName.contains("void") || typeName.contains(".v")) {
            return "return";
        } else {
            return "areturn";
        }
    }
}