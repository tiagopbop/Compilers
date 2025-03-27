package pt.up.fe.comp2025.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;

import static pt.up.fe.comp2025.ast.Kind.*;

public class JmmSymbolTableBuilder {

    // In case we want to already check for some semantic errors during symbol table building.
    private List<Report> reports;

    public List<Report> getReports() {
        return reports;
    }

    private static Report newError(JmmNode node, String message) {
        return Report.newError(
                Stage.SEMANTIC,
                node.getLine(),
                node.getColumn(),
                message,
                null);
    }

    public JmmSymbolTable build(JmmNode root) {

        reports = new ArrayList<>();

        var imports = buildImports(root);
        var classDecl = root.getChildren(Kind.CLASS_DECL).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("No class declaration found"));
        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        String className = classDecl.get("name");
        String superClassName = classDecl.hasAttribute("superclass") ? classDecl.get("superclass") : null;
        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);
        var fields = buildFields(classDecl);

        return new JmmSymbolTable(className, superClassName, methods, returnTypes, params, locals, imports, fields);
    }

    private List<String> buildImports(JmmNode root) {
        var imports = root.getChildren(Kind.IMPORT_DECL).stream()
                .map(importDecl -> String.join(".", (List<String>) importDecl.getObject("name")))
                .toList();

        return imports;
    }


    private Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> map = new HashMap<>();

        for (var method : classDecl.getChildren(Kind.METHOD_DECL)) {
            if ( method.getBoolean("isMain", false)) {
                map.put(method.get("method"), new Type("void", false));
            }
            else {
                map.put(method.get("method"), TypeUtils.convertType(method.getChildren().getFirst()));
            }
        }
        return map;
    }



    private Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        for (var method : classDecl.getChildren(Kind.METHOD_DECL)) {

            var params = method.getChildren(Kind.PARAM).stream()
                    .map(param -> {
                        var type = TypeUtils.convertType(param.getChild(0));
                        if (param.getKind().equals("VarArgParameter")) { type = new Type(type.getName(), true); }
                        return new Symbol(type, param.get("name"));
                    })
                    .toList();

            map.put(method.get("method"), params);
        }

        return map;
    }

    private Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {

        var map = new HashMap<String, List<Symbol>>();

        for (var method : classDecl.getChildren(Kind.METHOD_DECL)) {
            var name = method.get("method");
            var locals = method.getChildren(Kind.VAR_DECL).stream()
                    .map(varDecl -> new Symbol(TypeUtils.convertType(varDecl.getChildren().get(0)), varDecl.get("name")))
                    .toList();
            map.put(name, locals);
        }

        return map;
    }



    private List<String> buildMethods(JmmNode classDecl) {

        var methods = classDecl.getChildren(METHOD_DECL).stream()
                .map(method -> method.get("method"))
                .toList();

        return methods;
    }

    private List<Symbol> buildFields(JmmNode classDecl) {
        return classDecl.getChildren(VAR_DECL).stream()
                .map(varDecl -> new Symbol(new Type(varDecl.getChild(0).get("name"), Objects.equals(varDecl.getChild(0).getKind(), "VarArray")), varDecl.get("name")))
                .toList();
    }


}
