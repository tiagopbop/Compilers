package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp2025.ast.Kind;

import java.util.*;

public class ConstantPropagationVisitor extends AJmmVisitor<Void, Boolean> {

    private final Map<String, String> constants = new HashMap<>();
    private final Set<String> reassigned = new HashSet<>();

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit("MainMethodDeclaration", this::visitMethodDecl);
        setDefaultVisit(this::defaultVisit);
    }

    private Boolean visitMethodDecl(JmmNode method, Void unused) {
        constants.clear();
        reassigned.clear();
        String methodName = method.hasAttribute("method") ? method.get("method") : "main";
        boolean changed = false;
        Map<String, JmmNode> assignments = new HashMap<>();

        for (JmmNode stmt : method.getChildren()) {
            collectAssignments(stmt, assignments);
        }

        for (Map.Entry<String, JmmNode> entry : assignments.entrySet()) {
            String varName = entry.getKey();
            JmmNode assignStmt = entry.getValue();

            if (assignStmt.getKind().equals(Kind.ASSIGN_STMT.toString())) {
                JmmNode rhs = assignStmt.getChild(1);
                if (rhs.getKind().equals(Kind.INTEGER_LITERAL.toString())) {
                    // Check if this variable is only assigned once (is constant)
                    long assignmentCount = assignments.entrySet().stream()
                            .filter(e -> e.getKey().equals(varName))
                            .count();

                    if (assignmentCount == 1) {
                        constants.put(varName, rhs.get("value"));
                    }
                }
            }
        }

        for (JmmNode stmt : method.getChildren()) {
            boolean stmtChanged = replaceConstants(stmt);
            changed |= stmtChanged;
        }
        return changed;
    }

    private void collectAssignments(JmmNode node, Map<String, JmmNode> assignments) {
        if (node.getKind().equals(Kind.ASSIGN_STMT.toString())) {
            JmmNode lhs = node.getChild(0);
            if (lhs.getKind().equals(Kind.VAR_REF_EXPR.toString())) {
                String varName = lhs.get("name");
                assignments.put(varName, node);
            }
        }
        for (JmmNode child : node.getChildren()) {
            collectAssignments(child, assignments);
        }
    }

    private boolean replaceConstants(JmmNode node) {
        boolean changed = false;

        for (int i = 0; i < node.getNumChildren(); i++) {
            JmmNode child = node.getChild(i);

            if (child.getKind().equals(Kind.VAR_REF_EXPR.toString())) {
                String varName = child.get("name");
                if (constants.containsKey(varName)) {
                    String value = constants.get(varName);

                    JmmNode constNode = new JmmNodeImpl(Collections.singletonList(Kind.INTEGER_LITERAL.toString()));
                    constNode.put("value", value);

                    node.setChild(constNode, i);
                    changed = true;
                }
            } else {
                changed |= replaceConstants(child);
            }
        }

        return changed;
    }

    private Boolean defaultVisit(JmmNode node, Void unused) {
        boolean changed = false;
        for (JmmNode child : node.getChildren()) {
            changed |= visit(child);
        }
        return changed;
    }
}
