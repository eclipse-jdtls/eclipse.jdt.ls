package org.jboss.tools.vscode.java.handlers;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;
import org.jboss.tools.vscode.java.model.Position;
import org.jboss.tools.vscode.java.model.Range;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

import copied.org.eclipse.jdt.internal.corext.refactoring.util.TextEditUtil;

/**
 * @author IBM Corporation (Markus Keller)
 */
public class FormatterHandler extends AbstractRequestHandler {
	
	private static final String REQ_FORMATTING = "textDocument/formatting";

	@Override
	public boolean canHandle(String request) {
		return REQ_FORMATTING.equals(request);
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		List<org.jboss.tools.vscode.java.model.TextEdit> edits = format(request);
		JSONRPC2Response response = new JSONRPC2Response(request.getID());
		response.setResult(edits);
		return response;
	}

	@Override
	public void process(JSONRPC2Notification request) {
		// not needed
	}

	private List<org.jboss.tools.vscode.java.model.TextEdit> format(JSONRPC2Request request) {
		ICompilationUnit cu = resolveCompilationUnit(request);
		Map<String, String> eclipseOptions = getOptions(cu, request);
		CodeFormatter formatter = ToolFactory.createCodeFormatter(eclipseOptions);
		try {
			IDocument document = JsonRpcHelpers.toDocument(cu.getBuffer());
			String lineDelimiter = TextUtilities.getDefaultLineDelimiter(document);
			TextEdit format = formatter.format(CodeFormatter.K_COMPILATION_UNIT, document.get(), 0, document.getLength(), 0, lineDelimiter);
			MultiTextEdit flatEdit = TextEditUtil.flatten(format);
			return convertEdits(flatEdit.getChildren(), document);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
			return null;
		}
	}

	private Map<String, String> getOptions(ICompilationUnit cu, JSONRPC2Request request) {
		Map<String, String> eclipseOptions = cu.getJavaProject().getOptions(true);
		@SuppressWarnings("unchecked")
		Map<String, Object> namedParams = (Map<String, Object>) request.getNamedParams().get("options");
		Long tabSize = (Long) namedParams.get("tabSize");
		if (tabSize != null && tabSize > 0) {
			eclipseOptions.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, tabSize.toString());
		}
		Boolean insertSpaces = (Boolean) namedParams.get("insertSpaces");
		if (insertSpaces != null) {
			eclipseOptions.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, insertSpaces ? JavaCore.SPACE : JavaCore.TAB);
		}
		return eclipseOptions;
	}


	private List<org.jboss.tools.vscode.java.model.TextEdit> convertEdits(TextEdit[] edits, IDocument document) {
		return Arrays.stream(edits).map(t -> convertEdit(t, document)).collect(Collectors.toList());
	}

	private org.jboss.tools.vscode.java.model.TextEdit convertEdit(TextEdit edit, IDocument document) {
		org.jboss.tools.vscode.java.model.TextEdit textEdit = new org.jboss.tools.vscode.java.model.TextEdit();
		if (edit instanceof ReplaceEdit) {
			ReplaceEdit replaceEdit = (ReplaceEdit) edit;
			textEdit.setNewText(replaceEdit.getText());
			Range range = new Range();
			int offset = edit.getOffset();
			range.start = createPosition(document, offset);
			range.end = createPosition(document, offset + edit.getLength());
			textEdit.setRange(range);
		}
		return textEdit;
	}


	private Position createPosition(IDocument document, int offset) {
		Position start =  new Position();
		try {
			int lineOfOffset = document.getLineOfOffset(offset);
			start.line = lineOfOffset;
			start.character = offset - document.getLineOffset(lineOfOffset);
		} catch (BadLocationException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return start;
	}
}
