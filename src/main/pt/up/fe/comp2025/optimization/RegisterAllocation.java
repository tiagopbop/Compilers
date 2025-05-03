package pt.up.fe.comp2025.optimization;

import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.Descriptor;
import org.specs.comp.ollir.Method;

import java.util.Map;

public class RegisterAllocation {

    private final ClassUnit ollirClass;
    private final int maxRegisters;

    public RegisterAllocation(ClassUnit ollirClass, int maxRegisters) {
        this.ollirClass = ollirClass;
        this.maxRegisters = maxRegisters;
    }

    public void allocateRegisters() {
        for (Method method : ollirClass.getMethods()) {
            allocateMethod(method);
        }
    }

    private void allocateMethod(Method method) {
        if (maxRegisters < 0) {
            return;
        }

        Map<String, Descriptor> varTable = method.getVarTable();
        int registerCounter = 0;

        for (Map.Entry<String, Descriptor> entry : varTable.entrySet()) {
            String varName = entry.getKey();
            Descriptor descriptor = entry.getValue();

            if ("this".equals(varName)) {
                continue;
            }

            if (maxRegisters > 0) {
                descriptor.setVirtualReg(registerCounter % maxRegisters);
            } else {
                descriptor.setVirtualReg(registerCounter);
            }

            registerCounter++;
        }
    }
}
