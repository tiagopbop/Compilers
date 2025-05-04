package pt.up.fe.comp2025.optimization;

import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.Descriptor;
import org.specs.comp.ollir.Method;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.*;

public class RegisterAllocation {

    private final OllirResult ollirResult;
    private final int maxRegisters;

    public RegisterAllocation(OllirResult ollirResult, int maxRegisters) {
        this.ollirResult = ollirResult;
        this.maxRegisters = maxRegisters;
    }

    public void allocateRegisters() {
        ClassUnit classUnit = ollirResult.getOllirClass();
        classUnit.buildVarTables();

        for (Method method : classUnit.getMethods()) {
            if (method.isConstructMethod()) continue;

            System.out.println("\n>>> Allocating registers for method: " + method.getMethodName());

            Map<String, Descriptor> varTable = method.getVarTable();
            Map<String, String> aliasMap = new HashMap<>();

            method.getInstructions().forEach(instr -> {
                String instrStr = instr.toString().trim();

                if (!instrStr.contains("ASSIGN")) return;
                if (!instrStr.contains("Operand:") || !instrStr.contains("= ")) return;

                try {
                    String[] lhsParts = instrStr.split("Operand:")[1].split("=")[0].trim().split("\\.");
                    String[] rhsParts = instrStr.split("Operand:")[2].trim().split("\\.")[0].split("\\s+");

                    String lhs = lhsParts[0].trim();
                    String rhs = rhsParts[rhsParts.length - 1].trim();

                    if (!lhs.startsWith("tmp") && !rhs.matches("\\d+") &&
                            varTable.containsKey(lhs) && varTable.containsKey(rhs)) {
                        aliasMap.put(lhs, rhs);
                        System.out.println("Alias detected: " + lhs + " := " + rhs);
                    }

                } catch (Exception ignored) {}
            });

            Map<String, String> rootMap = new HashMap<>();
            for (String var : varTable.keySet()) {
                if (var.equals("this")) continue;
                String root = findRoot(var, aliasMap);
                rootMap.put(var, root);
                System.out.println("Variable " + var + " -> root alias: " + root);
            }

            Map<String, Integer> regAssignment = new HashMap<>();
            int regCounter = 0;
            for (String var : varTable.keySet()) {
                if (var.equals("this")) continue;
                String root = rootMap.getOrDefault(var, var);
                regAssignment.putIfAbsent(root, regCounter++);
                int reg = regAssignment.get(root);
                varTable.get(var).setVirtualReg(reg);
                System.out.println("Assigned register " + reg + " to variable " + var);
            }


            System.out.println(">>> Finished register allocation for " + method.getMethodName());
        }
    }

    private String findRoot(String var, Map<String, String> aliasMap) {
        Set<String> seen = new HashSet<>();
        String current = var;
        while (aliasMap.containsKey(current) && !seen.contains(current)) {
            seen.add(current);
            current = aliasMap.get(current);
        }
        return current;
    }



}
