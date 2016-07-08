package org.jboss.tools.vscode.java.managers;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.ui.search.IOccurrencesFinder.OccurrenceLocation;
import org.eclipse.jdt.internal.ui.search.IOccurrencesFinder;
import org.eclipse.jdt.internal.ui.search.OccurrencesFinder;
import org.jboss.tools.vscode.ipc.JsonRpcConnection;
import org.jboss.tools.vscode.java.CompletionProposalRequestor;
import org.jboss.tools.vscode.java.HoverInfoProvider;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;
import org.jboss.tools.vscode.java.handlers.DiagnosticsHandler;
import org.jboss.tools.vscode.java.handlers.JsonRpcHelpers;
import org.jboss.tools.vscode.java.model.CodeCompletionItem;
import org.jboss.tools.vscode.java.model.DocumentHighlight;
import org.jboss.tools.vscode.java.model.Location;
import org.jboss.tools.vscode.java.model.Position;
import org.jboss.tools.vscode.java.model.Range;
import org.jboss.tools.vscode.java.model.SymbolInformation;

/**
 * Manages the life-cycle of documents edited on VS Code.
 * 
 * @author Gorkem Ercan
 *
 */
public class DocumentsManager {

	private Map<String, ICompilationUnit> openUnits;
	private ProjectsManager pm;
	private JsonRpcConnection connection;

	public DocumentsManager(JsonRpcConnection conn, ProjectsManager pm) {
		openUnits = new HashMap<String, ICompilationUnit>();
		this.pm = pm;
		this.connection = conn;
	}

	public ICompilationUnit openDocument(String uri) {
		JavaLanguageServerPlugin.logInfo("Opening document : " + uri);
		ICompilationUnit unit = openUnits.get(uri);
		if (unit == null) {
			File f = null;
			try {
				f = URIUtil.toFile(new URI(uri));
			} catch (URISyntaxException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			if (f != null) {
				IPath p = Path.fromOSString(f.getAbsolutePath());
				IFile file = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(p);
				if (file != null) {
					try {
						final DiagnosticsHandler pe = new DiagnosticsHandler(connection, uri);
						unit = ((ICompilationUnit) JavaCore.create(file)).getWorkingCopy(new WorkingCopyOwner() {

							@Override
							public IProblemRequestor getProblemRequestor(ICompilationUnit workingCopy) {
								return pe;
							}

						}, new NullProgressMonitor());

					} catch (JavaModelException e) {
						// TODO: handle exception

					}
					openUnits.put(uri, unit);
					JavaLanguageServerPlugin.logInfo("added unit " + uri);
				}
			}
		}
		if (unit != null) {
			try {
				unit.reconcile(ICompilationUnit.NO_AST, true, null, null);
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException("Probem with reconcile for" + uri, e);
			}
		}
		return unit;

	}

	public void closeDocument(String uri) {
		JavaLanguageServerPlugin.logInfo("close document : " + uri);
		openUnits.remove(uri);
	}

	public void updateDocument(String uri, int line, int column, int length, String text) {
		JavaLanguageServerPlugin.logInfo("Updating document: " + uri + " line: " + line + " col:" + column + " length:"
				+ length + " text:" + text);
		ICompilationUnit unit = openUnits.get(uri);
		if (unit == null)
			return;
		try {
			IBuffer buffer = unit.getBuffer();
			int offset = JsonRpcHelpers.toOffset(buffer, line, column);
			buffer.replace(offset, length, text);
			JavaLanguageServerPlugin.logInfo("Changed buffer: " + buffer.getContents());

			if (length > 0 || text.length() > 0) {
				JavaLanguageServerPlugin.logInfo(uri + " updated reconciling");
				unit.reconcile(ICompilationUnit.NO_AST, true, null, null);
			}

		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem updating document " + uri, e);
		}
	}

	public List<DocumentHighlight> computeOccurrences(String uri, int line, int column) {
		ICompilationUnit unit = openUnits.get(uri);
		if (unit != null) {
			try {
				int offset = JsonRpcHelpers.toOffset(unit.getBuffer(), line, column);
				OccurrencesFinder finder = new OccurrencesFinder();
				ASTParser parser = ASTParser.newParser(AST.JLS8);
				parser.setSource(unit);
				parser.setResolveBindings(true);
				ASTNode ast = parser.createAST(new NullProgressMonitor());
				if (ast instanceof CompilationUnit) {
					finder.initialize((CompilationUnit) ast, offset, 0);
					List<DocumentHighlight> result = new ArrayList<>();
					OccurrenceLocation[] occurrences = finder.getOccurrences();
					if (occurrences != null) {
						for (OccurrenceLocation loc : occurrences) {
							result.add(convertToHighlight(unit, loc));
						}
					}
					return result;
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException("Problem with compute occurrences for" + uri, e);
			}
		}
		return Collections.emptyList();
	}

	private DocumentHighlight convertToHighlight(ICompilationUnit unit, OccurrenceLocation occurrence)
			throws JavaModelException {
		DocumentHighlight h = new DocumentHighlight();
		// TODO Auto-generated method stub
		if ((occurrence.getFlags() | IOccurrencesFinder.F_WRITE_OCCURRENCE) == IOccurrencesFinder.F_WRITE_OCCURRENCE) {
			h.kind = 3;
		} else if ((occurrence.getFlags()
				| IOccurrencesFinder.F_READ_OCCURRENCE) == IOccurrencesFinder.F_READ_OCCURRENCE) {
			h.kind = 2;
		}
		int[] loc = JsonRpcHelpers.toLine(unit.getBuffer(), occurrence.getOffset());
		int[] endLoc = JsonRpcHelpers.toLine(unit.getBuffer(), occurrence.getOffset() + occurrence.getLength());

		h.range = new Range();
		h.range.start = new Position(loc[0], loc[1]);
		h.range.end = new Position(endLoc[0], endLoc[1]);
		return h;
	}

	public List<CodeCompletionItem> computeContentAssist(String uri, int line, int column) {
		ICompilationUnit unit = openUnits.get(uri);
		if (unit == null)
			return Collections.emptyList();
		final List<CodeCompletionItem> proposals = new ArrayList<CodeCompletionItem>();
		try {
			CompletionRequestor collector = new CompletionProposalRequestor(unit, proposals);
			// Allow completions for unresolved types - since 3.3
			collector.setAllowsRequiredProposals(CompletionProposal.FIELD_REF, CompletionProposal.TYPE_REF, true);
			collector.setAllowsRequiredProposals(CompletionProposal.FIELD_REF, CompletionProposal.TYPE_IMPORT, true);
			collector.setAllowsRequiredProposals(CompletionProposal.FIELD_REF, CompletionProposal.FIELD_IMPORT, true);

			collector.setAllowsRequiredProposals(CompletionProposal.METHOD_REF, CompletionProposal.TYPE_REF, true);
			collector.setAllowsRequiredProposals(CompletionProposal.METHOD_REF, CompletionProposal.TYPE_IMPORT, true);
			collector.setAllowsRequiredProposals(CompletionProposal.METHOD_REF, CompletionProposal.METHOD_IMPORT, true);

			collector.setAllowsRequiredProposals(CompletionProposal.CONSTRUCTOR_INVOCATION, CompletionProposal.TYPE_REF,
					true);

			collector.setAllowsRequiredProposals(CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION,
					CompletionProposal.TYPE_REF, true);
			collector.setAllowsRequiredProposals(CompletionProposal.ANONYMOUS_CLASS_DECLARATION,
					CompletionProposal.TYPE_REF, true);

			collector.setAllowsRequiredProposals(CompletionProposal.TYPE_REF, CompletionProposal.TYPE_REF, true);

			unit.codeComplete(JsonRpcHelpers.toOffset(unit.getBuffer(), line, column), collector,
					new NullProgressMonitor());
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem with codeComplete for" + uri, e);
		}
		return proposals;

	}

	public String computeHover(String uri, int line, int column) {
		ICompilationUnit unit = openUnits.get(uri);
		HoverInfoProvider provider = new HoverInfoProvider(unit);
		return provider.computeHover(line, column);

	}

	public Location computeDefinitonNavigation(String uri, int line, int column) {
		ICompilationUnit unit = openUnits.get(uri);

		try {
			IJavaElement[] elements = unit.codeSelect(JsonRpcHelpers.toOffset(unit.getBuffer(), line, column), 0);

			if (elements == null || elements.length != 1)
				return null;
			IJavaElement element = elements[0];
			IResource resource = element.getResource();

			// if the selected element corresponds to a resource in workspace,
			// navigate to it
			if (resource != null && resource.getProject() != null) {
				return getLocation(unit, element);
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem with codeSelect for" + uri, e);
		}
		return null;
	}

	/**
	 * @param unit
	 * @param element
	 * @param resource
	 * @param $
	 * @throws JavaModelException
	 */
	public Location getLocation(ICompilationUnit unit, IJavaElement element) throws JavaModelException {
		Location $ = new Location();
		$.setUri("file://" + element.getResource().getLocationURI().getPath());
		if (element instanceof ISourceReference) {
			ISourceRange nameRange = ((ISourceReference) element).getNameRange();
			int[] loc = JsonRpcHelpers.toLine(unit.getBuffer(), nameRange.getOffset());
			int[] endLoc = JsonRpcHelpers.toLine(unit.getBuffer(), nameRange.getOffset() + nameRange.getLength());

			if (loc != null) {
				$.setLine(loc[0]);
				$.setColumn(loc[1]);
			}
			if (endLoc != null) {
				$.setEndLine(endLoc[0]);
				$.setEndColumn(endLoc[1]);
			}
			return $;
		}
		return null;
	}

	public Location getLocation(ICompilationUnit unit, int offset, int length) throws JavaModelException {
		Location result = new Location();
		result.setUri("file://" + unit.getResource().getLocationURI().getPath());
		int[] loc = JsonRpcHelpers.toLine(unit.getBuffer(), offset);
		int[] endLoc = JsonRpcHelpers.toLine(unit.getBuffer(), offset + length);

		if (loc != null) {
			result.setLine(loc[0]);
			result.setColumn(loc[1]);
		}
		if (endLoc != null) {
			result.setEndLine(endLoc[0]);
			result.setEndColumn(endLoc[1]);
		}
		return result;
	}

	public SymbolInformation[] getOutline(String uri) {
		ICompilationUnit unit = openUnits.get(uri);
		try {
			IJavaElement[] elements = unit.getChildren();
			ArrayList<SymbolInformation> symbols = new ArrayList<SymbolInformation>(elements.length);
			collectChildren(unit, elements, symbols);
			return symbols.toArray(new SymbolInformation[symbols.size()]);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem getting outline for" + uri, e);
		}
		return new SymbolInformation[0];
	}

	/**
	 * @param unit
	 * @param elements
	 * @param symbols
	 * @throws JavaModelException
	 */
	private void collectChildren(ICompilationUnit unit, IJavaElement[] elements, ArrayList<SymbolInformation> symbols)
			throws JavaModelException {
		for (IJavaElement element : elements) {
			if (element.getElementType() == IJavaElement.TYPE) {
				collectChildren(unit, ((IType) element).getChildren(), symbols);
			}
			if (element.getElementType() != IJavaElement.FIELD && element.getElementType() != IJavaElement.METHOD) {
				continue;
			}
			SymbolInformation si = new SymbolInformation();
			si.setName(element.getElementName());
			si.setKind(SymbolInformation.mapKind(element));
			if (element.getParent() != null)
				si.setContainerName(element.getParent().getElementName());
			si.setLocation(getLocation(unit, element));
			symbols.add(si);
		}
	}

	public boolean isOpen(String uri) {
		return openUnits.containsKey(uri);
	}

	public IJavaElement findElementAtSelection(String uri, int line, int column) throws JavaModelException {
		ICompilationUnit unit = openUnits.get(uri);

		IJavaElement[] elements = unit.codeSelect(JsonRpcHelpers.toOffset(unit.getBuffer(), line, column), 0);

		if (elements == null || elements.length != 1)
			return null;
		return elements[0];

	}

}
