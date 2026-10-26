package org.halim.acsessgui.sgui.sglib;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


/**
 * Reads the resources' {@code manual.md}: an entry is a {@code # } title, then two blocks fenced by four
 * backticks, the summary and the details, both HTML. A line starting with {@code //} is skipped, and
 * what lies outside a block is ignored. An entry with fewer than two blocks is dropped.
 * */
public class ManualParser {

public record ManualEntry(String title, String summary, String details) {}

public static List<ManualEntry> loadManual() {
	List<ManualEntry> entries = new ArrayList<>();
	try(InputStream stream = ManualParser.class.getResourceAsStream("/manual.md")) {
		if(stream == null) {
			System.err.println("manual.md is not among the resources.");
			return entries;
		}
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
		String title = "";
		StringBuilder summary = new StringBuilder(), details = new StringBuilder();
		int blocks = 0;
		boolean inside = false;
		String line;
		while((line = reader.readLine()) != null) {
			if(line.startsWith("//")) { continue; }
			String trimmed = line.trim();
			if(!inside && trimmed.startsWith("# ")) {
				title = trimmed.substring(2).trim();
				summary.setLength(0);
				details.setLength(0);
				blocks = 0;
			} else if(trimmed.startsWith("````")) {
				if(!inside) {
					inside = true;
					blocks++;
				} else {
					inside = false;
					if(blocks == 2 && !title.isEmpty()) { entries.add(new ManualEntry(title, summary.toString().trim(), details.toString().trim())); }
				}
			} else if(inside) {
				(blocks == 1 ? summary : details).append(line).append('\n');
			}
		}
	} catch(Exception e) { System.err.println("manual.md could not be read: " + e.getMessage()); }
	return entries;
}

}
