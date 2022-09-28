package com.iwaiwa;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Verilog {
	private ArrayList<String> file = new ArrayList<String>();
	private ArrayList<String> result = new ArrayList<String>();
	private ArrayList<String> other_module = new ArrayList<String>();
	private String module_definition = null;
	private ArrayList<String> input_definition = new ArrayList<String>();
	private ArrayList<String> output_definition = new ArrayList<String>();
	private ArrayList<String> ppis = new ArrayList<String>();
	private ArrayList<String> ppos = new ArrayList<String>();
	private ArrayList<String> wire_definition = new ArrayList<String>();
	private ArrayList<String> ffs_definition = new ArrayList<String>();
	private ArrayList<String> ccs_definition = new ArrayList<String>();
	private ArrayList<String> assign_definition = new ArrayList<String>();
	private ArrayList<String> ppi_connect = new ArrayList<String>();
	private ArrayList<String> ppo_connect = new ArrayList<String>();
	private int number_of_additional_inv = 0;
	
	/**
	 * Verilog Netlistクラス
	 * @param filename 読み込むverilogネットリスト
	 */
	public Verilog( String filename, String top_module_name ) {
		try {
			BufferedReader br = new BufferedReader( new FileReader(filename) );
			String line = null;
			while( (line=br.readLine()) != null ) {
				file.add(line);
			}
			br.close();
		} catch( Exception e ) {
			e.printStackTrace();
		}
		if( top_module_name != null ) {
			ArrayList<String> top_module = new ArrayList<String>();
			boolean top_flag   = false;
			boolean other_flag = false;
			for( int i=0; i<file.size(); i++ ) {
				if( file.get(i).matches("\\s*module\\s+"+top_module_name+".*") ) {
					top_flag = true;
				} else if( file.get(i).matches("\\s*module\\s+.*") ) {
					other_flag = true;
				}
				if( top_flag ) {
					top_module.add(file.get(i));
				}
				if( other_flag ) {
					other_module.add(file.get(i));
				}
				if( file.get(i).matches("\\s*endmodule.*") ) {
					top_flag   = false;
					other_flag = false;
				}
			}
			file = top_module;
		}
	}
	
	public void printVerilog() {
		for( int i=0; i<result.size(); i++ ) {
			System.out.println(result.get(i));
		}
	}
	public void writeVerilog( String file ) {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(file));
			for( int i=0; i<result.size(); i++ ) {
				bw.write(result.get(i));
				bw.newLine();
			}
			bw.close();
		} catch( Exception e ) { e.printStackTrace(); }
	}
	
	/**
	 * 組合せ回路部分のみを抽出してファイルを作成
	 */
	public void generateCombCicuit() {
		this.extractDefinition();	// 記述を分割して抽出
		// PPIとPPOの作成
		PseudoPrimaryPinAccessor pppa= new PseudoPrimaryPinAccessor( ffs_definition );
		ppis = pppa.getPPIs();
		ppos = pppa.getPPOs();
		this.removeFilpFlops();
		module_definition = this.addPseudoPrimaryPinsToModuleDefinition();

		
		for( int i=0; i<other_module.size(); i++ ) {
			result.add(other_module.get(i));
		}
		result.add("");
		result.add(module_definition);
		result.add("// begining of the input definition.");
		for( int i=0; i<input_definition.size(); i++ ) {	result.add("\t " + input_definition.get(i)); }
		for( int i=0; i<ppis.size(); i++ ) {				result.add("\t " + ppis.get(i)); }
		result.add("// begining of the output definition.");
		for( int i=0; i<output_definition.size(); i++ ) { 	result.add("\t" + output_definition.get(i)); }
		for( int i=0; i<ppos.size(); i++ ) {				result.add("\t" + ppos.get(i)); }
		result.add("\n// begining of the wire definition.");
		for( int i=0; i<wire_definition.size(); i++ ) {		result.add("\t" + wire_definition.get(i).replaceAll("^\\s+", "")); }
		result.add("\n// begining of the combinational circuit");
		for( int i=0; i<ccs_definition.size(); i++ ) {		result.add("\t" + ccs_definition.get(i).replaceAll("^\\s+", "")); }
		result.add("\n// begining of the connection of internal wire");
		for( int i=0; i<assign_definition.size(); i++ ) {	result.add("\t" + assign_definition.get(i)); }
		result.add("\n// begining of the connection from ppi");
		for( int i=0; i<ppi_connect.size(); i++ ) {			result.add("\tassign " + ppi_connect.get(i)); }
		result.add("\n// begining of the connection to ppo");
		for( int i=0; i<ppo_connect.size(); i++ ) {			result.add("\tassign " + ppo_connect.get(i)); }
		result.add("endmodule");
	}
	

	/**
	 * 単一モジュールが記述されたファイルから定義を分割
	 */
	private void extractDefinition() {
		// 改行の除去
		ArrayList<String> rm_return = new ArrayList<String>();
		boolean flag = false;
		StringBuffer sb = new StringBuffer();
		for( int i=0; i<file.size(); i++ ) {
			sb.append(file.get(i));
			if( file.get(i).matches(".+;\\s*(//)?.*") || file.get(i).matches("^\\s*endmodule") ) {
				flag = true;
			}
			if( flag ) {
				flag = false;
				// スペースが２つ以上ある場合は除去(１つに変換)
				rm_return.add(sb.toString().replaceAll("\\s{2,}", " "));
				sb = new StringBuffer();
			}
		}

		// 定義の整理
		for( int i=0; i<rm_return.size(); i++ ) {
			if( rm_return.get(i).matches("\\s*module\\s+\\S+\\s*\\(.+\\);.*") ) {
				module_definition = rm_return.get(i);
			} else if( rm_return.get(i).matches("\\s*input\\s+.+;.*") ) {
				if( rm_return.get(i).matches(".*cl(oc)?k.*") ) {
					// clk文がある場合は取り除く
					if( !rm_return.get(i).matches("\\s*input\\s+cl(oc)?k\\s*;.*") ) {
						input_definition.add(rm_return.get(i).replaceFirst("(,\\s*)?cl(oc)?k(,\\s*)?", ""));
					}
				} else {
					input_definition.add(rm_return.get(i));
				}
			} else if( rm_return.get(i).matches("\\s*output\\s+.+;.*") ) {
				output_definition.add(rm_return.get(i));
			} else if( rm_return.get(i).matches("\\s*wire\\s+.+;.*") ) {
				wire_definition.add(rm_return.get(i));
			} else if( rm_return.get(i).matches("\\s*FD[12]\\s+\\S+\\s*\\(.+\\);.*") ) {
				ffs_definition.add(rm_return.get(i));
			} else if( rm_return.get(i).matches("\\s*\\w+\\s+\\S+\\s*\\(.+\\)\\s*;.*") ) {
				ccs_definition.add(rm_return.get(i));
			} else if( rm_return.get(i).matches("\\s*[A-Z\\d]+\\s+[\\S]+\\d+\\s*\\(.+\\);.*") ) {
				ccs_definition.add(rm_return.get(i));
			} else if( rm_return.get(i).matches("\\s*assign\\s+\\S+\\s*=\\s*.+;.*") ) {
				assign_definition.add(rm_return.get(i));
			} else if( rm_return.get(i).matches("\\s*endmodule.*") ) {
			} else {
				System.out.println("想定外の行：" + rm_return.get(i));
			}
		}
	}
	
	/**
	 * フリップフロップを除去して，PPIとPPOに接続します<br>
	 * ここでインタフェースはPPI PPOともにFF１つに対して１つずつ生成されます<br>
	 * FD2の場合，出力が２つになります <- edited at 4 Dec, 2007
	 */
	private void removeFilpFlops() {
		Pattern input_regex = Pattern.compile("\\s*FD[12]\\s+(\\S+)\\s*\\((.+)\\);.*");
		for( int i=0; i<ffs_definition.size(); i++ ) {
			Matcher input_match = input_regex.matcher(ffs_definition.get(i));
			if( input_match.matches() ) {
				String[] internal_pin = input_match.group(2).split(",");
				for( int j=0; j<internal_pin.length; j++ ) {
					this.connectPPIs(internal_pin[j], input_match.group(1).replaceFirst("\\\\\\w+/", "").replace('/', '_').replaceAll("\\\\", ""));
					this.connectPPOs(internal_pin[j], input_match.group(1).replaceFirst("\\\\\\w+/", "").replace('/', '_').replaceAll("\\\\", ""));
				}
			}
		}
	}
	
	/**
	 * PPIと現存のwireを接続します
	 * @param internal_pin FF内でのピン名とその接続情報
	 * @param reg_name FFの名前（PPIの名前生成に利用します）
	 */
	private void connectPPIs( String internal_pin, String reg_name ) {
		Pattern q_regex = Pattern.compile("\\s*\\.Q\\(\\s*(\\S+)\\s*\\)\\s*.*");
		Matcher q_match = q_regex.matcher(internal_pin);
		if( q_match.matches() ) {
			ppi_connect.add(q_match.group(1) + "\t= " + "ppi_" + reg_name + " ;");
		}
		Pattern qn_regex = Pattern.compile("\\s*\\.QN\\(\\s*(\\S+)\\s*\\)\\s*.*");
		Matcher qn_match = qn_regex.matcher(internal_pin);
		if( qn_match.matches() ) {
			ccs_definition.add("IV UN" + number_of_additional_inv + " ( .A( ppi_" + reg_name + " ), .Z( " + qn_match.group(1) + " ) );");
			number_of_additional_inv++;
		}
	}
	/**
	 * PPOと現存のwireを接続します
	 * @param internal_pin FF内でのピン名とその接続情報
	 * @param reg_name FFの名前（PPIの名前生成に利用します）
	 */
	private void connectPPOs( String internal_pin, String reg_name ) {
		Pattern d_regex = Pattern.compile("\\s*\\.D\\(\\s*(\\S+)\\s*\\)\\s*.*");
		Matcher d_match = d_regex.matcher(internal_pin);
		Pattern cd_regex = Pattern.compile("\\s*\\.CD\\(\\s*(\\S+)\\s*\\)\\s*.*");
		Matcher cd_match = cd_regex.matcher(internal_pin);
		if( d_match.matches() ) {
			ppo_connect.add("ppo_" + reg_name + "\t= " + d_match.group(1) + " ;");
		} else if( cd_match.matches() ) {
			Pattern cr_regex = Pattern.compile("([\\\\\\w/]+)(\\[(\\d+)\\])?");
			Matcher cr_match = cr_regex.matcher(reg_name);
			if( cr_match.matches() ) {
				ppo_connect.add("ppo_" + cr_match.group(1) + "_cd[" + cr_match.group(3) + "]\t= " + cd_match.group(1) + " ;");
			} else {
				ppo_connect.add("ppo_" + reg_name + "_cd\t= " + cd_match.group(1) + " ;");
			}
		}
	}
	
	/**
	 * 外部入出力ピンの指定に疑似外部入出力を付加します
	 * @return module定義文
	 */
	private String addPseudoPrimaryPinsToModuleDefinition() {
		Pattern modname_regex = Pattern.compile("\\s*module\\s+(\\S+)\\s*\\((.+)\\)\\s*;.*");
		Matcher modname_match = modname_regex.matcher(module_definition);
		String mod_name = "";
		LinkedList<String> primary_pins = new LinkedList<String>();
		if( modname_match.matches() ) {
			mod_name = modname_match.group(1);
			String[] pins = modname_match.group(2).split(",");
			for( int i=0; i<pins.length; i++ ) {
				// clk宣言は除去
				if( !pins[i].matches("\\s*cl(oc)?k\\s*") ) {
					primary_pins.add(pins[i].replaceAll(" ", ""));
				}
			}
		} else {
			System.out.println("Error: can't analyze the module info!");
		}
		for( int i=0; i<ppis.size(); i++ ) {
			primary_pins.add(ppis.get(i).replaceAll("input( \\[\\d+:\\d+\\] )?", "").replace(";", ""));
		}
		for( int i=0; i<ppos.size(); i++ ) {
			primary_pins.add(ppos.get(i).replaceAll("output( \\[\\d+:\\d+\\] )?", "").replace(";", ""));
		}
		Object[] sort_pis = primary_pins.toArray();
		Arrays.sort(sort_pis);
		
		StringBuffer sb = new StringBuffer();
		sb.append("module ");
		sb.append(mod_name);
		sb.append(" ( ");
//		for( int i=0; i<primary_pins.size(); i++ ) {
//			sb.append(primary_pins.get(i));
//			sb.append(" , ");
//		}
		for( int i=0; i<sort_pis.length; i++ ) {
			sb.append(sort_pis[i]);
			sb.append(" , ");
		}
		sb.delete(sb.length()-3, sb.length());
		sb.append(" );");
		
		return( sb.toString() );
	}
}
