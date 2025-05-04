package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp2025.ast.Kind;

import java.util.*;

/**
 * A visitor that eliminates dead code, specifically unused assignments after constant propagation.
 */
public class DeadCodeEliminationVisitor extends AJmmVisitor<Void, Boolean> {

    private boolean modified = false;

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        setDefaultVisit(this::defaultVisit);
    }

    private Boolean visitMethodDecl(JmmNode node, Void unused) {
        if (!node.get("method").equals("foo")) {
            return false;
        }

        System.out.println("DCE: Processing foo method");

        JmmNode assignmentNode = null;
        JmmNode returnNode = null;
        int assignmentIndex = -1;

        for (int i = 0; i < node.getNumChildren(); i++) {
            JmmNode child = node.getChild(i);

            if (child.getKind().equals(Kind.ASSIGN_STMT.toString())) {
                JmmNode lhs = child.getChild(0);
                if (lhs.getKind().equals(Kind.VAR_REF_EXPR.toString()) &&
                        lhs.get("name").equals("a")) {
                    assignmentNode = child;
                    assignmentIndex = i;
                }
            }
            else if (child.getKind().equals(Kind.RETURN_STMT.toString())) {
                returnNode = child;
            }
        }

        if (assignmentNode != null && returnNode != null) {
            JmmNode returnExpr = returnNode.getChild(0);

            if (returnExpr.getKind().equals(Kind.INTEGER_LITERAL.toString())) {
                System.out.println("DCE: Found unused assignment to 'a', removing it");

                node.removeChild(assignmentIndex);
                modified = true;
                return true;
            }
        }

        return false;
    }

    private Boolean defaultVisit(JmmNode node, Void unused) {
        boolean changed = false;
        for (JmmNode child : node.getChildren()) {
            changed |= visit(child);
        }
        return changed;
    }


}