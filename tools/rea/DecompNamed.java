// Décompile les fonctions dont le nom (démanglé ou non) contient un des motifs passés en argument.
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.listing.*;
import ghidra.app.cmd.disassemble.DisassembleCommand;
import ghidra.app.cmd.function.CreateFunctionCmd;
import java.io.*;

public class DecompNamed extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        PrintWriter out = new PrintWriter(new FileWriter(args[0]));
        DecompInterface d = new DecompInterface();
        d.openProgram(currentProgram);
        SymbolIterator it = currentProgram.getSymbolTable().getAllSymbols(true);
        while (it.hasNext()) {
            Symbol s = it.next();
            boolean hit = false;
            for (int i = 1; i < args.length; i++) if (s.getName(true).contains(args[i])) hit = true;
            if (!hit || s.getSymbolType() != SymbolType.FUNCTION && s.getSymbolType() != SymbolType.LABEL) continue;
            if (!currentProgram.getMemory().getBlock(s.getAddress()).isExecute()) continue;
            new DisassembleCommand(s.getAddress(), null, true).applyTo(currentProgram, monitor);
            Function f = getFunctionAt(s.getAddress());
            if (f == null) {
                new CreateFunctionCmd(s.getAddress()).applyTo(currentProgram, monitor);
                f = getFunctionAt(s.getAddress());
            }
            if (f == null) continue;
            DecompileResults r = d.decompileFunction(f, 120, monitor);
            out.println("// ===== " + s.getName(true) + " @ " + s.getAddress());
            out.println(r.getDecompiledFunction() != null ? r.getDecompiledFunction().getC() : r.getErrorMessage());
        }
        out.close();
    }
}
