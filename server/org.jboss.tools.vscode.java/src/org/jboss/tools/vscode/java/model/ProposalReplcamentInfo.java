package org.jboss.tools.vscode.java.model;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.text.edits.TextEdit;

/**
 * POJO for passing completion proposal replacement text info
 * 
 * @author aboyko
 * 
 * Copied from Flux project.
 */
class ProposalReplcamentInfo {
	
	public String replacement;
	public TextEdit extraChanges;
	public List<Integer> positions;
	
	public ProposalReplcamentInfo() {
		this.replacement = "";
		this.extraChanges = null;
		this.positions = new ArrayList<Integer>();
	}

}
