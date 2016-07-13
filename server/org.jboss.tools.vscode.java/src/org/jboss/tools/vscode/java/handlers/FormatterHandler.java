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
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
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
	private static final String REQ_RANGE_FORMATTING = "textDocument/rangeFormatting";

	@Override
	public boolean canHandle(String request) {
		return REQ_FORMATTING.equals(request)
				|| REQ_RANGE_FORMATTING.equals(request);
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
		Map<String, String> eclipseOptions = getOptions(request, cu);
		CodeFormatter formatter = ToolFactory.createCodeFormatter(eclipseOptions);
		try {
			IDocument document = JsonRpcHelpers.toDocument(cu.getBuffer());
			String lineDelimiter = TextUtilities.getDefaultLineDelimiter(document);
			IRegion region = getRegion(request, document);
			TextEdit format = formatter.format(CodeFormatter.K_COMPILATION_UNIT, document.get(), region.getOffset(), region.getLength(), 0, lineDelimiter);
			MultiTextEdit flatEdit = TextEditUtil.flatten(format);
			return convertEdits(flatEdit.getChildren(), document);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private IRegion getRegion(JSONRPC2Request request, IDocument document) {
		if (REQ_RANGE_FORMATTING.equals(request.getMethod())) {
			Map<String, Object> range = (Map<String, Object>) request.getNamedParams().get("range");
			Map<String, Object> start = (Map<String, Object>) range.get("start");
			Long line = (Long) start.get("line");
			Long character = (Long) start.get("character");
			try {
				int offset = document.getLineOffset(line.intValue()) + character.intValue();
				Map<String, Object> end = (Map<String, Object>) range.get("end");
				line = (Long) end.get("line");
				character = (Long) end.get("character");
				int endOffset = document.getLineOffset(line.intValue()) + character.intValue();
				int length = endOffset - offset;
				return new Region(offset, length);
			} catch (BadLocationException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
		}
		return new Region(0, document.getLength());
	}

	private static Map<String, String> getOptions(JSONRPC2Request request, ICompilationUnit cu) {
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


	private static List<org.jboss.tools.vscode.java.model.TextEdit> convertEdits(TextEdit[] edits, IDocument document) {
		return Arrays.stream(edits).map(t -> convertEdit(t, document)).collect(Collectors.toList());
	}

	private static org.jboss.tools.vscode.java.model.TextEdit convertEdit(TextEdit edit, IDocument document) {
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


	private static Position createPosition(IDocument document, int offset) {
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
