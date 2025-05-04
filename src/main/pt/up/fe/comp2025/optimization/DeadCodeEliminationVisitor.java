package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp2025.ast.Kind;

import java.util.*;

/**
 * A visitor that eliminates dead code, specifically code that appears after return statements.
 */
public class DeadCodeEliminationVisitor extends AJmmVisitor<Void, Boolean> {

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        setDefaultVisit(this::defaultVisit);
    }

    private Boolean visitMethodDecl(JmmNode node, Void unused) {
        boolean methodChanged = false;
        String methodName = node.get("method");


        boolean foundReturn = false;
        List<Integer> toRemove = new ArrayList<>();

        for (int i = 0; i < node.getNumChildren(); i++) {
            JmmNode child = node.getChild(i);

            if (foundReturn) {
                toRemove.add(i);
            } else if (child.getKind().equals(Kind.RETURN_STMT.toString())) {
                foundReturn = true;
            }
        }

        for (int i = toRemove.size() - 1; i >= 0; i--) {
            int index = toRemove.get(i);
            node.removeChild(index);
            methodChanged = true;
        }

        return methodChanged;
    }

    private Boolean defaultVisit(JmmNode node, Void unused) {
        boolean changed = false;
        for (JmmNode child : node.getChildren()) {
            changed |= visit(child);
        }
        return changed;
    }
}