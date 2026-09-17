package org.eclipse.jdt.ls.core.internal.lombok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JsonMessageHelper;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.handlers.AbstractCompilationUnitBasedTest;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentLifeCycleHandler;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.CompletionItemDefaults;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.InsertTextMode;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class LombokCompletionHandlerTest extends AbstractCompilationUnitBasedTest {

	private DocumentLifeCycleHandler lifeCycleHandler;
	private JavaClientConnection javaClient;
	private static String COMPLETION_TEMPLATE =
			"{\n" +
					"    \"id\": \"1\",\n" +
					"    \"method\": \"textDocument/completion\",\n" +
					"    \"params\": {\n" +
					"        \"textDocument\": {\n" +
					"            \"uri\": \"${file}\"\n" +
					"        },\n" +
					"        \"position\": {\n" +
					"            \"line\": ${line},\n" +
					"            \"character\": ${char}\n" +
					"        }\n" +
					"    },\n" +
					"    \"jsonrpc\": \"2.0\"\n" +
					"}";

	@BeforeEach
	public void setUp() {
		mockLSP3Client();
		CoreASTProvider sharedASTProvider = CoreASTProvider.getInstance();
		sharedASTProvider.disposeAST();
		//		sharedASTProvider.clearASTCreationCount();
		javaClient = new JavaClientConnection(client);
		lifeCycleHandler = new DocumentLifeCycleHandler(javaClient, preferenceManager, projectsManager, true);
		preferences.setPostfixCompletionEnabled(false);
		preferences.setCompletionLazyResolveTextEditEnabled(false);
		Preferences.DISCOVERED_STATIC_IMPORTS.clear();
	}

	@AfterEach
	public void tearDown() throws Exception {
	}




	@Test
	public void testCompletion_lombok() throws Exception {
		when(preferenceManager.getClientPreferences().isCompletionInsertReplaceSupport()).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsSupport()).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsPropertySupport("editRange")).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsPropertySupport("insertTextFormat")).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionItemInsertTextModeSupport(InsertTextMode.AdjustIndentation)).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsPropertySupport("insertTextMode")).thenReturn(true);
		importProjects("maven/mavenlombok");
		IProject proj = WorkspaceHelper.getProject("mavenlombok");
		IJavaProject javaProject = JavaCore.create(proj);
		ICompilationUnit unit = null;
		try {
			unit = (ICompilationUnit) javaProject.findElement(new Path("org/sample/Test.java"));
			unit.becomeWorkingCopy(null);
			String source =
			//@formatter:off
				"package org.sample;\n"
				+ "import lombok.Builder;\n"
				+ "import lombok.Data;\n"
				+ "import lombok.Builder.Default;\n"
				+ "@Data\n"
				+ "@Builder\n"
				+ "public class Test {\n"
				+ "      @Default\n"
				+ "      private Integer offset = ;\n"
				+ "}\n";
			//@formatter:on
			changeDocument(unit, source, 1);
			Job.getJobManager().join(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, new NullProgressMonitor());
			CompletionList list = requestCompletions(unit, " = ");
			assertNotNull(list);
			assertEquals(6, list.getItems().size());
			CompletionItemDefaults itemDefaults = list.getItemDefaults();
			assertNotNull(itemDefaults);
			assertNull(itemDefaults.getInsertTextFormat());
			assertNull(itemDefaults.getEditRange());
		} finally {
			unit.discardWorkingCopy();
			proj.delete(true, monitor);
		}
	}

	@Test
	public void testCompletion_lombok2() throws Exception {
		when(preferenceManager.getClientPreferences().isCompletionInsertReplaceSupport()).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsSupport()).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsPropertySupport("editRange")).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsPropertySupport("insertTextFormat")).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionItemInsertTextModeSupport(InsertTextMode.AdjustIndentation)).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsPropertySupport("insertTextMode")).thenReturn(true);
		importProjects("maven/mavenlombok");
		IProject proj = WorkspaceHelper.getProject("mavenlombok");
		IJavaProject javaProject = JavaCore.create(proj);
		ICompilationUnit unit = null;
		try {
			unit = (ICompilationUnit) javaProject.findElement(new Path("org/sample/Test.java"));
			unit.becomeWorkingCopy(null);
			String source =
			//@formatter:off
					"package org.sample;\n"
					+ "import lombok.Builder;\n"
					+ "import lombok.Data;\n"
					+ "import lombok.Builder.Default;\n"
					+ "@Data\n"
					+ "@Builder\n"
					+ "public class Test {\n"
					+ "      private Integer offset = ;\n"
					+ "}\n";
				//@formatter:on
			changeDocument(unit, source, 1);
			Job.getJobManager().join(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, new NullProgressMonitor());
			CompletionList list = requestCompletions(unit, " = ");
			assertNotNull(list);
			assertEquals(19, list.getItems().size());
			CompletionItemDefaults itemDefaults = list.getItemDefaults();
			assertNotNull(itemDefaults);
			assertNotNull(itemDefaults.getEditRange());
		} finally {
			unit.discardWorkingCopy();
			proj.delete(true, monitor);
		}
	}



	private void changeDocument(ICompilationUnit unit, String content, int version) throws JavaModelException {
		DidChangeTextDocumentParams changeParms = new DidChangeTextDocumentParams();
		VersionedTextDocumentIdentifier textDocument = new VersionedTextDocumentIdentifier();
		textDocument.setUri(JDTUtils.toURI(unit));
		textDocument.setVersion(version);
		changeParms.setTextDocument(textDocument);
		TextDocumentContentChangeEvent event = new TextDocumentContentChangeEvent();
		event.setText(content);
		List<TextDocumentContentChangeEvent> contentChanges = new ArrayList<>();
		contentChanges.add(event);
		changeParms.setContentChanges(contentChanges);
		lifeCycleHandler.didChange(changeParms);
	}

	private CompletionList requestCompletions(ICompilationUnit unit, String completeBehind) throws JavaModelException {
		return requestCompletions(unit, completeBehind, 0);
	}

	private CompletionList requestCompletions(ICompilationUnit unit, String completeBehind, int fromIndex) throws JavaModelException {
		int[] loc = findCompletionLocation(unit, completeBehind, fromIndex);
		return server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
	}

	private String createCompletionRequest(ICompilationUnit unit, int line, int kar) {
		return COMPLETION_TEMPLATE.replace("${file}", JDTUtils.toURI(unit))
				.replace("${line}", String.valueOf(line))
				.replace("${char}", String.valueOf(kar));
	}

	private void mockLSP3Client() {
		mockLSPClient(true, true);
	}

	private void mockLSP2Client() {
		mockLSPClient(false, false);
	}

	private void mockLSPClient(boolean isSnippetSupported, boolean isSignatureHelpSupported) {
		// Mock the preference manager to use LSP v3 support.
		when(preferenceManager.getClientPreferences().isCompletionSnippetsSupported()).thenReturn(isSnippetSupported);
		when(preferenceManager.getClientPreferences().isSignatureHelpSupported()).thenReturn(isSignatureHelpSupported);
	}
}
