package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;

import java.util.Collections;

public class ConstantFoldingVisitor extends AJmmVisitor<Void, Boolean> {

    private int foldCounter = 0;

    @Override
    protected void buildVisitor() {
        addVisit("BinaryExpr", this::visitBinaryExpr);
        setDefaultVisit(this::defaultVisit);
    }

    private Boolean visitBinaryExpr(JmmNode node, Void unused) {
        JmmNode left = node.getChild(0);
        JmmNode right = node.getChild(1);
        String op = node.get("operation");

        boolean leftFolded = visit(left);
        boolean rightFolded = visit(right);

        if (leftFolded || rightFolded) {
            left = node.getChild(0);
            right = node.getChild(1);
        }

        boolean isInt = left.getKind().equals("IntegerLiteral") && right.getKind().equals("IntegerLiteral");
        boolean isBool = left.getKind().equals("BooleanLiteral") && right.getKind().equals("BooleanLiteral");

        if (!isInt && !isBool) return false;

        JmmNode foldedNode;

        if (isInt) {
            int lval = Integer.parseInt(left.get("value"));
            int rval = Integer.parseInt(right.get("value"));
            int result;

            switch (op) {
                case "+" -> result = lval + rval;
                case "-" -> result = lval - rval;
                case "*" -> result = lval * rval;
                case "/" -> result = rval != 0 ? lval / rval : 0;
                default -> { return false; }
            }

            foldedNode = new JmmNodeImpl(Collections.singletonList("IntegerLiteral"));
            foldedNode.put("value", String.valueOf(result));

        } else {
            boolean lval = Boolean.parseBoolean(left.get("value"));
            boolean rval = Boolean.parseBoolean(right.get("value"));
            boolean result;

            switch (op) {
                case "&&" -> result = lval && rval;
                case "||" -> result = lval || rval;
                default -> { return false; }
            }

            foldedNode = new JmmNodeImpl(Collections.singletonList("BooleanLiteral"));
            foldedNode.put("value", String.valueOf(result));
        }

        JmmNode parent = node.getParent();
        int index = node.getIndexOfSelf();
        parent.setChild(foldedNode, index);
        foldCounter++;

        return true;
    }

    private Boolean defaultVisit(JmmNode node, Void unused) {
        boolean changed = false;
        for (JmmNode child : node.getChildren()) {
            if (visit(child)) changed = true;
        }
        return changed;
    }
}
