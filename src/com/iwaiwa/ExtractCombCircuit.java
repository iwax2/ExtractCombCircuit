package com.iwaiwa;

public class ExtractCombCircuit {
	public static String usage = "usage: extract_comb_circuit <input_verilog> [-w <output_verilog>] [-t <topmodule>]";

	/**
	 * @param args 引数<br>args[0] : 入力するverilogファイル名
	 */
	public static void main(String[] args) {
		if( args.length == 0 ) {
			System.out.println(usage);
			return;
		}
		String input_file  = args[0];
		String output_file = null;
		String top_module  = null;

		for( int i=0; i<args.length; i++ ) {
			if( args[i].equals("-h") || args[i].equals("-help") ) {
				System.out.println(usage);
				return;
			}
			if( args[i].equals("-w") ) {
				if( i+1 < args.length ) {
					output_file = args[i+1];
				} else {
					System.out.println(usage);
					return;
				}
			}
			if( args[i].equals("-t") ) {
				if( i+1 < args.length ) {
					top_module = args[i+1];
				} else {
					System.out.println(usage);
					return;
				}
			}
		}
		Verilog v = new Verilog( input_file, top_module );
		v.generateCombCicuit();
		if( output_file != null ) {
			v.writeVerilog( output_file );
		} else {
			v.printVerilog();
		}
	}
}
