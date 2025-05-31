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
import org.specs.comp.ollir.inst.UnaryOpInstruction;
import org.specs.comp.ollir.ArrayOperand;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private int currentStackSize = 0;
    private int maxStackSize = 0;
    private int maxLocals = 0;
    private int currentMethodLabelCounter = 0;
    private int labelCounter = 0;

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
        generators.put(UnaryOpInstruction.class, this::generateUnaryOp);

    }

    private String apply(TreeNode node) {
        var code = new StringBuilder();
        code.append(generators.apply(node));
        return code.toString();
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {
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

        for (var method : ollirResult.getOllirClass().getMethods()) {
            if (method.isConstructMethod()) {
                continue;
            }
            code.append(apply(method));
        }

        return code.toString();
    }

    private String generateMethod(Method method) {
        currentMethod = method;
        currentMethodLabelCounter = 0;

        var code = new StringBuilder();

        // reset limits tracking
        currentStackSize = 0;
        maxStackSize = 0;
        maxLocals = calculateMaxLocals(method);

        // calculate modifier
        var modifier = types.getModifier(method.getMethodAccessModifier());
        var methodName = method.getMethodName();

        if (method.isStaticMethod()) {
            modifier += "static ";
        }

        //method signaure
        StringBuilder signature = new StringBuilder();
        signature.append("(");

        for (Element param : method.getParams()) {
            String paramType = getJasminType(param.getType());
            signature.append(paramType);
        }
        signature.append(")");
        String returnTypeJasmin = getJasminType(method.getReturnType());
        signature.append(returnTypeJasmin);

        code.append("\n.method ").append(modifier)
                .append(methodName).append(signature).append(NL);

        Map<Integer, List<String>> labelsBefore = parseLabelsFromOllir(method);

        // method body with label insertion
        StringBuilder bodyCode = new StringBuilder();
        for (int i = 0; i < method.getInstructions().size(); i++) {
            var inst = method.getInstructions().get(i);

            if (labelsBefore.containsKey(i)) {
                for (String label : labelsBefore.get(i)) {
                    bodyCode.append(TAB).append(label).append(NL);
                }
            }
            String instCode = apply(inst);
            if (!instCode.trim().isEmpty()) {
                var lines = StringLines.getLines(instCode).stream()
                        .collect(Collectors.joining(NL + TAB, TAB, NL));
                bodyCode.append(lines);
            }
        }
        int finalStackLimit = maxStackSize;

        if (finalStackLimit < 1) {
            finalStackLimit = 1;
        }

        code.append(TAB).append(".limit stack ").append(finalStackLimit).append(NL);
        code.append(TAB).append(".limit locals ").append(maxLocals).append(NL);

        code.append(bodyCode);
        code.append(".end method\n");

        // unset method
        currentMethod = null;
        return code.toString();
    }
    private String generateUnaryOp(UnaryOpInstruction unaryOp) {
        var code = new StringBuilder();

        code.append(apply(unaryOp.getOperand()));

        var opType = unaryOp.getOperation().getOpType();

        switch (opType) {
            case NOTB:
                String trueLabel = "not_true_" + (++labelCounter);
                String endLabel = "not_end_" + (++labelCounter);

                code.append("ifeq ").append(trueLabel).append(NL);
                code.append("iconst_0").append(NL);
                code.append("goto ").append(endLabel).append(NL);
                code.append(trueLabel).append(":").append(NL);
                code.append("iconst_1").append(NL);
                code.append(endLabel).append(":").append(NL);

                updateStackSize(0);
                break;

            default:
                throw new NotImplementedException("Unary operation: " + opType);
        }

        return code.toString();
    }

    private Map<Integer, List<String>> parseLabelsFromOllir(Method method) {
        Map<Integer, List<String>> labelsBefore = new HashMap<>();

        try {
            String ollirCode = ollirResult.getOllirCode();
            String methodName = method.getMethodName();

            String[] lines = ollirCode.split("\n");

            boolean inMethod = false;
            List<String> methodLines = new ArrayList<>();

            for (String line : lines) {
                if (line.trim().startsWith(".method") && line.contains(methodName)) {
                    inMethod = true;
                    continue;
                }
                if (inMethod && line.trim().equals("}")) {
                    break;
                }
                if (inMethod) {
                    methodLines.add(line.trim());
                }
            }

            List<Instruction> instructions = method.getInstructions();
            int instIndex = 0;

            for (String line : methodLines) {
                if (line.isEmpty()) continue;

                if (line.endsWith(":") && line.matches("[a-zA-Z_][a-zA-Z0-9_]*:")) {
                    labelsBefore.computeIfAbsent(instIndex, k -> new ArrayList<>()).add(line);
                } else if (!line.startsWith(".") && !line.startsWith("//") && !line.startsWith(";")) {
                    instIndex++;
                }
            }

        } catch (Exception e) {
            System.err.println("Warning: Could not parse labels from OLLIR: " + e.getMessage());
        }

        return labelsBefore;
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
        var lhs = assign.getDest();

        if (lhs instanceof ArrayOperand) {
            ArrayOperand arrayOp = (ArrayOperand) lhs;

            String arrayName = arrayOp.getName();

            var arrayReg = currentMethod.getVarTable().get(arrayName);
            if (arrayReg != null) {
                String arrayLoadInst = getLoadInstruction(arrayReg.getVarType(), arrayReg.getVirtualReg());
                code.append(arrayLoadInst).append(NL);
                updateStackSize(1);
            }

            var indexOperands = arrayOp.getIndexOperands();
            if (!indexOperands.isEmpty()) {
                code.append(apply(indexOperands.get(0)));
            }

            code.append(apply(assign.getRhs()));

            String storeInst = getArrayStoreInstruction(arrayOp.getType());
            code.append(storeInst).append(NL);

            updateStackSize(-3); // consumes array ref, index, and value
            return code.toString();
        }

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

        if (assign.getRhs() instanceof CallInstruction) {
            CallInstruction rhsCall = (CallInstruction) assign.getRhs();
            if (rhsCall.toString().contains("New caller")) {
                code.append(apply(assign.getRhs()));

                var reg = currentMethod.getVarTable().get(operand.getName());
                String storeInst = getStoreInstruction(operand.getType(), reg.getVirtualReg());
                code.append(storeInst).append(NL);
                updateStackSize(-1);
                return code.toString();
            }
        }

        code.append(apply(assign.getRhs()));

        if (assign.getRhs() instanceof BinaryOpInstruction) {
            BinaryOpInstruction binOp = (BinaryOpInstruction) assign.getRhs();
            if (binOp.getOperation().getOpType().toString().equals("ADD")) {
                Element left = binOp.getLeftOperand();
                Element right = binOp.getRightOperand();

                if (left instanceof Operand && right instanceof LiteralElement) {
                    String constValue = ((LiteralElement) right).getLiteral();
                    if (constValue.equals("1")) {
                        String leftVarName = ((Operand) left).getName();
                        var leftReg = currentMethod.getVarTable().get(leftVarName);

                        code = new StringBuilder();
                        code.append("iinc ").append(leftReg.getVirtualReg()).append(" 1").append(NL);
                        updateStackSize(0);
                        return code.toString();
                    }
                }
            }
        }

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
        var code = new StringBuilder();

        if (operand instanceof ArrayOperand) {
            ArrayOperand arrayOp = (ArrayOperand) operand;

            String arrayName = arrayOp.getName();

            var arrayReg = currentMethod.getVarTable().get(arrayName);
            if (arrayReg != null) {
                String arrayLoadInst = getLoadInstruction(arrayReg.getVarType(), arrayReg.getVirtualReg());
                code.append(arrayLoadInst).append(NL);
                updateStackSize(1);
            }

            var indexOperands = arrayOp.getIndexOperands();
            if (!indexOperands.isEmpty()) {
                code.append(apply(indexOperands.get(0)));
            }

            String loadInst = getArrayLoadInstruction(arrayOp.getType());
            code.append(loadInst).append(NL);

            updateStackSize(-1);
            return code.toString();
        }

        if (isFieldAccess(operand)) {
            code.append("aload_0").append(NL);
            code.append("getfield ").append(currentMethod.getOllirClass().getClassName())
                    .append("/").append(operand.getName()).append(" ")
                    .append(getJasminType(operand.getType())).append(NL);
            updateStackSize(1);
            return code.toString();
        }

        var reg = currentMethod.getVarTable().get(operand.getName());
        if (reg == null) {
            throw new RuntimeException("Variable not found in var table: " + operand.getName());
        }

        updateStackSize(1);
        String loadInst = getLoadInstruction(operand.getType(), reg.getVirtualReg());
        return loadInst + NL;
    }
    private String getArrayStoreInstruction(Type elementType) {
        String typeName = elementType.toString().toLowerCase();
        if (typeName.contains("int") || typeName.contains("i32")) {
            return "iastore";
        } else if (typeName.contains("bool") || typeName.contains("boolean")) {
            return "bastore";
        } else if (typeName.contains("char")) {
            return "castore";
        } else if (typeName.contains("byte")) {
            return "bastore";
        } else if (typeName.contains("short")) {
            return "sastore";
        } else if (typeName.contains("long")) {
            return "lastore";
        } else if (typeName.contains("float")) {
            return "fastore";
        } else if (typeName.contains("double")) {
            return "dastore";
        } else {
            return "aastore";
        }
    }

    private String getArrayLoadInstruction(Type elementType) {
        String typeName = elementType.toString().toLowerCase();
        if (typeName.contains("int") || typeName.contains("i32")) {
            return "iaload";
        } else if (typeName.contains("bool") || typeName.contains("boolean")) {
            return "baload";
        } else if (typeName.contains("char")) {
            return "caload";
        } else if (typeName.contains("byte")) {
            return "baload";
        } else if (typeName.contains("short")) {
            return "saload";
        } else if (typeName.contains("long")) {
            return "laload";
        } else if (typeName.contains("float")) {
            return "faload";
        } else if (typeName.contains("double")) {
            return "daload";
        } else {
            return "aaload";
        }
    }
    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        if (isComparisonOp(binaryOp.getOperation().getOpType())) {
            return generateComparison(binaryOp);
        }

        code.append(apply(binaryOp.getLeftOperand()));
        code.append(apply(binaryOp.getRightOperand()));

        String jasminOp = getJasminOperation(binaryOp.getOperation().getOpType());
        code.append(jasminOp).append(NL);
        updateStackSize(-1);

        return code.toString();
    }

    private String generateComparison(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        code.append(apply(binaryOp.getLeftOperand()));
        code.append(apply(binaryOp.getRightOperand()));

        String trueLabel = "true_" + currentMethod.getMethodName() + "_" + (++currentMethodLabelCounter);
        String endLabel = "end_" + currentMethod.getMethodName() + "_" + (++currentMethodLabelCounter);

        if (binaryOp.getRightOperand() instanceof LiteralElement) {
            LiteralElement rightLit = (LiteralElement) binaryOp.getRightOperand();
            if (rightLit.getLiteral().equals("0")) {
                code = new StringBuilder();
                code.append(apply(binaryOp.getLeftOperand()));

                String compareInst = types.getSingleOperandComparisonInstruction(binaryOp.getOperation().getOpType());
                if (compareInst == null) {
                    throw new NotImplementedException(binaryOp.getOperation().getOpType());
                }

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

        String compareInst = types.getComparisonInstruction(binaryOp.getOperation().getOpType());
        if (compareInst == null) {
            throw new NotImplementedException(binaryOp.getOperation().getOpType());
        }

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

        String returnType = getReturnInstruction(currentMethod.getReturnType());
        code.append(returnType).append(NL);

        if (returnInst.hasReturnValue()) {
            updateStackSize(-1);
        }

        return code.toString();
    }


    private String generateCall(CallInstruction call) {
        JasminUtils.JasminCallType callType = types.getCallType(call);

        return switch (callType) {
            case NEW_OBJECT -> generateNewObject(call);
            case NEW_ARRAY -> generateNewArray(call);
            case INVOKE_STATIC -> generateInvokeStatic(call);
            case INVOKE_SPECIAL -> generateInvokeSpecial(call);
            case INVOKE_VIRTUAL -> generateInvokeVirtual(call);
            case ARRAY_LENGTH -> generateArrayLength(call);
        };
    }
    private String generateArrayLength(CallInstruction call) {
        var code = new StringBuilder();
        var operands = call.getOperands();

        if (operands.size() > 0) {
            code.append(apply(operands.get(0)));
        }

        code.append("arraylength").append(NL);

        updateStackSize(0);
        return code.toString();
    }

    private String generateNewObject(CallInstruction call) {
        var code = new StringBuilder();

        String className = extractClassName(call);

        code.append("new ").append(className).append(NL);
        code.append("dup").append(NL);
        updateStackSize(2);

        return code.toString();
    }

    private String generateNewArray(CallInstruction call) {
        var code = new StringBuilder();
        var operands = call.getOperands();

        if (operands.size() > 1) {
            code.append(apply(operands.get(1)));
        } else {
            code.append("iconst_1").append(NL);
            updateStackSize(1);
        }

        code.append("newarray int").append(NL);

        updateStackSize(0);
        return code.toString();
    }

    private String generateInvokeStatic(CallInstruction call) {
        var code = new StringBuilder();
        var operands = call.getOperands();

        MethodCallInfo methodInfo = extractMethodCallInfo(call);

        for (int i = methodInfo.argStartIndex; i < operands.size(); i++) {
            code.append(apply(operands.get(i)));
        }

        code.append("invokestatic ")
                .append(methodInfo.className).append("/").append(methodInfo.methodName)
                .append(buildMethodSignature(call, methodInfo.argStartIndex))
                .append(NL);

        updateStackForMethodCall(call, operands.size() - methodInfo.argStartIndex);
        return code.toString();
    }

    private String generateInvokeSpecial(CallInstruction call) {
        var code = new StringBuilder();
        var operands = call.getOperands();

        MethodCallInfo methodInfo = extractMethodCallInfo(call);

        code.append(apply(operands.get(0)));

        for (int i = methodInfo.argStartIndex; i < operands.size(); i++) {
            code.append(apply(operands.get(i)));
        }

        code.append("invokespecial ")
                .append(methodInfo.className).append("/").append(methodInfo.methodName)
                .append(buildMethodSignature(call, methodInfo.argStartIndex))
                .append(NL);

        updateStackForMethodCall(call, operands.size() - methodInfo.argStartIndex + 1);
        return code.toString();
    }

    private String generateInvokeVirtual(CallInstruction call) {
        var code = new StringBuilder();
        var operands = call.getOperands();

        MethodCallInfo methodInfo = extractMethodCallInfo(call);

        int stackBeforeArgs = currentStackSize;

        code.append(apply(operands.get(0)));

        for (int i = methodInfo.argStartIndex; i < operands.size(); i++) {
            code.append(apply(operands.get(i)));
        }

        int argsPushed = (operands.size() - methodInfo.argStartIndex) + 1; // +1 for object ref

        code.append("invokevirtual ")
                .append(methodInfo.className).append("/").append(methodInfo.methodName)
                .append(buildMethodSignature(call, methodInfo.argStartIndex))
                .append(NL);

        updateStackSize(-argsPushed);

        if (!call.getReturnType().toString().toLowerCase().contains("void")) {
            updateStackSize(1);
        }

        return code.toString();
    }

    private static class MethodCallInfo {
        String className;
        String methodName;
        int argStartIndex;

        MethodCallInfo(String className, String methodName, int argStartIndex) {
            this.className = className;
            this.methodName = methodName;
            this.argStartIndex = argStartIndex;
        }
    }


    private MethodCallInfo extractMethodCallInfo(CallInstruction call) {
        var operands = call.getOperands();
        String className;
        if (operands.get(0) instanceof Operand) {
            Operand classOperand = (Operand) operands.get(0);
            String operandName = classOperand.getName();

            boolean isImport = ollirResult.getOllirClass().getImports().stream()
                    .anyMatch(imp -> imp.equals(operandName) || imp.endsWith("." + operandName));

            if (isImport) {
                className = operandName;
            } else {
                className = types.getClassName(operands.get(0));
            }
        } else {
            className = types.getClassName(operands.get(0));
        }

        String methodName;
        int argStartIndex = 1;
        if (operands.size() > 1 && operands.get(1) instanceof LiteralElement) {
            methodName = ((LiteralElement) operands.get(1)).getLiteral().replace("\"", "");
            argStartIndex = 2;
        } else {
            try {
                methodName = call.getMethodName().toString().replace("\"", "");
            } catch (Exception e) {
                throw new RuntimeException("Could not determine method name for call: " + call);
            }
        }
        return new MethodCallInfo(className, methodName, argStartIndex);
    }

    private String extractClassName(CallInstruction call) {
        var operands = call.getOperands();
        return types.getClassName(operands.get(0));
    }

    private String buildMethodSignature(CallInstruction call, int argStartIndex) {
        var operands = call.getOperands();
        var signature = new StringBuilder("(");

        for (int i = argStartIndex; i < operands.size(); i++) {
            signature.append(types.toJasminType(operands.get(i).getType()));
        }

        signature.append(")");
        signature.append(types.toJasminType(call.getReturnType()));

        return signature.toString();
    }

    private void updateStackForMethodCall(CallInstruction call, int argsConsumed) {
        updateStackSize(-argsConsumed);
        if (!call.getReturnType().toString().toLowerCase().contains("void")) {
            updateStackSize(1);
        }
    }


    private String getJasminType(Type type) {
        return types.toJasminType(type);
    }

    private String getLoadInstruction(Type type, int reg) {
        return types.getLoadInstruction(type, reg);
    }

    private String getStoreInstruction(Type type, int reg) {
        return types.getStoreInstruction(type, reg);
    }

    private String getReturnInstruction(Type returnType) {
        return types.getReturnInstruction(returnType);
    }

    private String getConstantLoadInstruction(int value) {
        return types.getConstantLoadInstruction(value) + NL;
    }

    private String getJasminOperation(org.specs.comp.ollir.OperationType opType) {
        String operation = types.getJasminOperation(opType);
        if (operation == null) {
            throw new NotImplementedException(opType);
        }
        return operation;
    }

    private boolean isComparisonOp(org.specs.comp.ollir.OperationType opType) {
        return types.isComparisonOp(opType);
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
}