package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.CallInstruction;
import org.specs.comp.ollir.type.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.Map;

public class JasminUtils {

    private final OllirResult ollirResult;

    // Our own call type enum
    public enum JasminCallType {
        NEW_OBJECT, NEW_ARRAY, INVOKE_STATIC, INVOKE_SPECIAL, INVOKE_VIRTUAL, ARRAY_LENGTH
    }

    // Type mapping for primitive types
    private static final Map<String, String> PRIMITIVE_TYPE_MAP = Map.of(
            "INT32", "I",
            "BOOLEAN", "Z",
            "VOID", "V",
            "STRING", "Ljava/lang/String;"
    );

    // Instruction mapping for operations
    private static final Map<OperationType, String> OPERATION_MAP = Map.of(
            OperationType.ADD, "iadd",
            OperationType.SUB, "isub",
            OperationType.MUL, "imul",
            OperationType.DIV, "idiv",
            OperationType.AND, "iand",
            OperationType.OR, "ior"
    );

    // Comparison operations
    private static final Map<OperationType, String> COMPARISON_MAP = Map.of(
            OperationType.LTH, "if_icmplt",
            OperationType.GTH, "if_icmpgt",
            OperationType.EQ, "if_icmpeq",
            OperationType.NEQ, "if_icmpne",
            OperationType.LTE, "if_icmple",
            OperationType.GTE, "if_icmpge"
    );

    // Single operand comparison operations
    private static final Map<OperationType, String> SINGLE_COMPARISON_MAP = Map.of(
            OperationType.LTH, "iflt",
            OperationType.GTH, "ifgt",
            OperationType.EQ, "ifeq",
            OperationType.NEQ, "ifne",
            OperationType.LTE, "ifle",
            OperationType.GTE, "ifge"
    );

    public JasminUtils(OllirResult ollirResult) {
        this.ollirResult = ollirResult;
    }

    /**
     * Converts OLLIR type to Jasmin type descriptor
     */
    public String toJasminType(Type type) {
        String typeName = type.toString();
        String lowerTypeName = typeName.toLowerCase();

        if (typeName.contains("[]") || lowerTypeName.contains("array")) {
            if (lowerTypeName.contains("int") || lowerTypeName.contains("i32")) {
                return "[I";
            } else if (lowerTypeName.contains("bool")) {
                return "[Z";
            } else if (lowerTypeName.contains("string")) {
                return "[Ljava/lang/String;";
            }
            return "[I";
        }

        String upperTypeName = typeName.toUpperCase();
        if (PRIMITIVE_TYPE_MAP.containsKey(upperTypeName)) {
            return PRIMITIVE_TYPE_MAP.get(upperTypeName);
        }

        if (lowerTypeName.contains("int") || lowerTypeName.contains("i32")) {
            return "I";
        } else if (lowerTypeName.contains("bool")) {
            return "Z";
        } else if (lowerTypeName.contains("void") || lowerTypeName.contains(".v")) {
            return "V";
        } else if (lowerTypeName.contains("string")) {
            return "Ljava/lang/String;";
        }
        if (typeName.startsWith("OBJECTREF(") && typeName.endsWith(")")) {
            String className = typeName.substring(10, typeName.length() - 1);
            return "L" + className + ";";
        }
        if (type instanceof ClassType) {
            ClassType classType = (ClassType) type;
            String className = classType.getName();
            return "L" + className + ";";
        }
        String className = typeName.replace("\"", "").trim();
        if (className.contains(".")) {
            className = className.substring(className.lastIndexOf('.') + 1);
        }
        if (className.equals("<init>") || className.startsWith("<") || className.isEmpty()) {
            return "Ljava/lang/Object;";
        }

        return "L" + className + ";";
    }

    /**
     * Gets Jasmin operation for OLLIR operation type
     */
    public String getJasminOperation(OperationType opType) {
        return OPERATION_MAP.get(opType);
    }

    /**
     * Gets Jasmin comparison instruction
     */
    public String getComparisonInstruction(OperationType opType) {
        return COMPARISON_MAP.get(opType);
    }

    /**
     * Gets single operand comparison instruction
     */
    public String getSingleOperandComparisonInstruction(OperationType opType) {
        return SINGLE_COMPARISON_MAP.get(opType);
    }

    /**
     * Determines if operation is a comparison
     */
    public boolean isComparisonOp(OperationType opType) {
        return COMPARISON_MAP.containsKey(opType);
    }

    /**
     * Gets appropriate load instruction for type and register
     */
    public String getLoadInstruction(Type type, int reg) {
        String baseInst = isReferenceType(type) ? "aload" : "iload";
        return formatRegisterInstruction(baseInst, reg);
    }

    /**
     * Gets appropriate store instruction for type and register
     */
    public String getStoreInstruction(Type type, int reg) {
        String baseInst = isReferenceType(type) ? "astore" : "istore";
        return formatRegisterInstruction(baseInst, reg);
    }

    /**
     * Gets appropriate return instruction for type
     */
    public String getReturnInstruction(Type type) {
        String typeName = type.toString().toLowerCase();

        if (typeName.contains("int") || typeName.contains("i32") || typeName.contains("bool")) {
            return "ireturn";
        } else if (typeName.contains("void") || typeName.contains(".v")) {
            return "return";
        } else {
            return "areturn";
        }
    }

    /**
     * Gets constant load instruction for integer values
     */
    public String getConstantLoadInstruction(int value) {
        if (value >= -1 && value <= 5) {
            return "iconst_" + (value == -1 ? "m1" : value);
        } else if (value >= -128 && value <= 127) {
            return "bipush " + value;
        } else if (value >= -32768 && value <= 32767) {
            return "sipush " + value;
        } else {
            return "ldc " + value;
        }
    }

    /**
     * Determines if type is a reference type (object/array)
     */
    private boolean isReferenceType(Type type) {
        String typeName = type.toString().toLowerCase();

        if (typeName.contains("objectref") || typeName.contains("array") || typeName.contains("[]")) {
            return true;
        }
        if (type instanceof ClassType || type instanceof ArrayType) {
            return true;
        }
        if (typeName.contains("string")) {
            return true;
        }

        return false;
    }

    /**
     * Formats register instruction (e.g. iload_0 vs iload 4)
     */
    private String formatRegisterInstruction(String baseInst, int reg) {
        if (reg <= 3) {
            return baseInst + "_" + reg;
        }
        return baseInst + " " + reg;
    }

    /**
     * Gets access modifier string
     */
    public String getModifier(AccessModifier accessModifier) {
        return accessModifier != AccessModifier.DEFAULT ?
                accessModifier.name().toLowerCase() + " " :
                "";
    }

    /**
     * Extracts clean class name from various OLLIR representations
     */
    public String getClassName(Element element) {
        if (element instanceof Operand) {
            Operand operand = (Operand) element;
            Type type = operand.getType();

            if (type instanceof ClassType) {
                return ((ClassType) type).getName();
            }

            String typeStr = type.toString();
            String extractedName = extractClassNameFromTypeString(typeStr);
            if (extractedName != null) {
                return extractedName;
            }
        }

        String str = element.toString();
        String extractedName = extractClassNameFromString(str);
        if (extractedName != null) {
            return extractedName;
        }

        return str;
    }

    private String extractClassNameFromTypeString(String typeStr) {
        if (typeStr.startsWith("OBJECTREF(") && typeStr.endsWith(")")) {
            return typeStr.substring(10, typeStr.length() - 1);
        }

        if (typeStr.contains(".") && !typeStr.contains("array")) {
            return typeStr.substring(typeStr.lastIndexOf('.') + 1);
        }

        if (!typeStr.contains("[]") && !typeStr.toLowerCase().contains("array")
                && !isPrimitiveType(typeStr)) {
            return typeStr.replace("\"", "").trim();
        }

        return null;
    }

    private String extractClassNameFromString(String str) {
        if (str.contains("OBJECTREF(") && str.contains(")")) {
            int start = str.indexOf("OBJECTREF(") + 10;
            int end = str.indexOf(")", start);
            if (end > start) {
                return str.substring(start, end);
            }
        }

        if (str.contains(".") && !str.contains("array")) {
            String[] parts = str.split("\\.");
            return parts[parts.length - 1].replace("\"", "").trim();
        }

        String cleaned = str.replace("\"", "").trim();
        if (cleaned.contains(":")) {
            cleaned = cleaned.substring(cleaned.lastIndexOf(":") + 1).trim();
        }

        if (cleaned.equals("<init>") || cleaned.startsWith("<") || cleaned.isEmpty()
                || isPrimitiveType(cleaned)) {
            return "Object";
        }

        return cleaned;
    }

    private boolean isPrimitiveType(String typeName) {
        String lower = typeName.toLowerCase();
        return lower.contains("int") || lower.contains("i32") || lower.contains("bool")
                || lower.contains("void") || lower.contains("string");
    }

    /**
     * Determines the type of call instruction using systematic analysis
     */
    public JasminCallType getCallType(CallInstruction call) {
        String callStr = call.toString();
        Type returnType = call.getReturnType();

        if (callStr.contains("ArrayLength")) {
            return JasminCallType.ARRAY_LENGTH;
        }

        if (callStr.contains("New caller")) {
            var operands = call.getOperands();
            if (operands.size() > 0) {
                String firstOperandStr = operands.get(0).toString();
                if (firstOperandStr.toLowerCase().contains("array.") ||
                        firstOperandStr.contains("[]") ||
                        returnType instanceof ArrayType) {
                    return JasminCallType.NEW_ARRAY;
                }
            }
            return JasminCallType.NEW_OBJECT;
        }

        if (callStr.contains("InvokeStatic")) {
            return JasminCallType.INVOKE_STATIC;
        }

        if (callStr.contains("InvokeSpecial")) {
            return JasminCallType.INVOKE_SPECIAL;
        }

        if (callStr.contains("InvokeVirtual")) {
            return JasminCallType.INVOKE_VIRTUAL;
        }

        var operands = call.getOperands();
        if (operands.size() > 1 && operands.get(1) instanceof LiteralElement) {
            String methodName = ((LiteralElement) operands.get(1)).getLiteral();
            if ("<init>".equals(methodName)) {
                return JasminCallType.INVOKE_SPECIAL;
            }
        }

        if (operands.size() > 0 && operands.get(0) instanceof Operand) {
            Operand firstOperand = (Operand) operands.get(0);
            String operandName = firstOperand.getName();

            if (ollirResult.getOllirClass().getImports().contains(operandName)) {
                return JasminCallType.INVOKE_STATIC;
            }

            String typeStr = firstOperand.getType().toString();
            if (typeStr.contains("OBJECTREF") || typeStr.contains("CLASS")) {
                return JasminCallType.INVOKE_VIRTUAL;
            }
        }

        return JasminCallType.INVOKE_STATIC;
    }

}