package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

/**
 * Utility methods regarding types.
 */
public class TypeUtils {


    private final JmmSymbolTable table;

    public TypeUtils(SymbolTable table) {
        this.table = (JmmSymbolTable) table;
    }

    public static Type newIntType() {
        return new Type("int", false);
    }

    public static Type convertType(JmmNode typeNode) {

        // TODO: When you support new types, this must be updated
        var name = typeNode.get("name");
        var isArray = typeNode.getKind().equals("ArrayType");

        return new Type(name, isArray);
    }


    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @return
     */
    public Type getExprType(JmmNode expr) {
        if (expr.getKind().equals(Kind.ARRAY_ACCESS)) {
            JmmNode arrayExpr = expr.getChild(0);
            Type arrayType = getExprType(arrayExpr);

            if (!arrayType.isArray()) {
                throw new SemanticException("Cannot access element of non-array type: " + arrayType.getName());
            }
            return arrayType;
        }
        return new Type("int", false);

    }


    public class SemanticException extends RuntimeException {
        public SemanticException(String message) {
            super(message);
        }
    }




}
