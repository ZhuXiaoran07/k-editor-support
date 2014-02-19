package ro.uaic.fmse.kplugin.psi;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ro.uaic.fmse.kplugin.KFileType;

import java.util.*;

/**
 * @author Denis Bogdanas
 *         Created on 12/11/13.
 */
public class KPsiUtil {

    @NotNull
    public static List<KVarDec> findVarDecsInSameRule(PsiElement element, String name) {
        KRule rule = getRule(element);
        if (rule != null) {
            return findVarDecs(name, rule);
        } else {
            return Collections.emptyList();
        }
    }

    private static KRule getRule(PsiElement element) {
        while (element != null && !(element instanceof KRule) && !(element instanceof KModule) &&
                !(element instanceof PsiFile)) {
            element = element.getParent();
        }
        return (element instanceof KRule) ? (KRule) element : null;
    }

    @NotNull
    private static List<KVarDec> findVarDecs(String name, KRule rule) {
        List<KVarDec> result = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Collection<KVarDec> typedVars = PsiTreeUtil.collectElementsOfType(rule.getRuleBody(), KVarDec.class);
        for (KVarDec varDec : typedVars) {
            if (varDec.getId() != null && name.equals(varDec.getId().getText())) {
                result.add(varDec);
            }
        }
        return result;
    }

    public static List<KRegularProduction> findSyntaxDefs(Project project, String name) {
        List<KRegularProduction> result = null;
        Collection<VirtualFile> virtualFiles = FileBasedIndex.getInstance().getContainingFiles(FileTypeIndex.NAME,
                KFileType.INSTANCE, GlobalSearchScope.allScope(project));
        for (VirtualFile virtualFile : virtualFiles) {
            KFile kFile = (KFile) PsiManager.getInstance(project).findFile(virtualFile);
            if (kFile != null) {
                Collection<KRegularProduction> syntaxDefs =
                        PsiTreeUtil.findChildrenOfType(kFile, KRegularProduction.class);
                for (KRegularProduction syntaxDef : syntaxDefs) {
                    if (name == null || name.equals(syntaxDef.getName())) {
                        if (result == null) {
                            result = new ArrayList<>();
                        }
                        result.add(syntaxDef);
                    }
                }
            }
        }
        return result != null ? result : Collections.<KRegularProduction>emptyList();
    }

    public static List<KRegularProduction> findSyntaxDefs(Project project) {
        return findSyntaxDefs(project, null);
    }

    //todo bug, here and other similar functions. If this file contains no require causes it could be a file referred
    //todo by other files as well. If at least one other k file refers this one, search in the whole project.
    @Nullable
    public static KRegularProduction findFirstSyntaxDef(KFile refFile, String... names) {
        //If the file have no "require" clauses resolve the reference in the current file scope.
        //Otherwise resolve it in the module scope.
        List<String> namesList = Arrays.asList(names);
        Collection<VirtualFile> virtualFiles = refFile.getRequires().size() > 0
                ? FileBasedIndex.getInstance().getContainingFiles(FileTypeIndex.NAME,
                KFileType.INSTANCE, getModule(refFile).getModuleContentScope())
                : Arrays.asList(refFile.getVirtualFile());
        for (VirtualFile virtualFile : virtualFiles) {
            KFile kFile = (KFile) PsiManager.getInstance(refFile.getProject()).findFile(virtualFile);
            if (kFile != null) {
                Collection<KRegularProduction> syntaxDefs =
                        PsiTreeUtil.findChildrenOfType(kFile, KRegularProduction.class);
                for (KRegularProduction syntaxDef : syntaxDefs) {
                    if (namesList.contains(syntaxDef.getName())) {
                        return syntaxDef;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    public static KSyntax findFirstSyntax(KFile refFile, String... names) {
        //If the file have no "require" clauses resolve the reference in the current file scope.
        //Otherwise resolve it in the module scope.
        List<String> namesList = Arrays.asList(names);
        Collection<VirtualFile> virtualFiles = refFile.getRequires().size() > 0
                ? FileBasedIndex.getInstance().getContainingFiles(FileTypeIndex.NAME,
                KFileType.INSTANCE, getModule(refFile).getModuleContentScope())
                : Arrays.asList(refFile.getVirtualFile());
        for (VirtualFile virtualFile : virtualFiles) {
            KFile kFile = (KFile) PsiManager.getInstance(refFile.getProject()).findFile(virtualFile);
            if (kFile != null) {
                Collection<KSyntax> syntaxNodes = PsiTreeUtil.findChildrenOfType(kFile, KSyntax.class);
                for (KSyntax syntax : syntaxNodes) {
                    if (namesList.contains(syntax.getName())) {
                        return syntax;
                    }
                }
            }
        }
        return null;
    }

    public static Module getModule(PsiElement element) {
        return ProjectRootManager.getInstance(element.getProject()).getFileIndex()
                .getModuleForFile(element.getContainingFile().getVirtualFile());
    }

    @NotNull
    public static ResolveResult[] resolveAuxFunctions(PsiReference psiReference, String name) {
        KRegularProduction syntaxDef = findFirstSyntaxDef((KFile) psiReference.getElement().getContainingFile(), name);
        return syntaxDef != null ? new ResolveResult[]{new PsiElementResolveResult(syntaxDef)} : new ResolveResult[0];
    }

    @NotNull
    public static ResolveResult[] resolveLabelDec(PsiReference psiReference, String labelName) {
        List<String> labelDecNames = new ArrayList<>(3);
        labelDecNames.add(labelName);
        String labelWithoutQuote = labelName.substring(1);
        labelDecNames.add(labelWithoutQuote);

        int backQuotePos = labelWithoutQuote.indexOf('`');
        if (backQuotePos != -1) {
            labelDecNames.add(labelWithoutQuote.substring(0, backQuotePos));
        }

        KRegularProduction syntaxDef = findFirstSyntaxDef((KFile) psiReference.getElement().getContainingFile(),
                labelDecNames.toArray(new String[labelDecNames.size()]));
        return syntaxDef != null ? new ResolveResult[]{new PsiElementResolveResult(syntaxDef)} : new ResolveResult[0];
    }

    public static ResolveResult[] resolveSyntax(PsiReference psiReference, String sort) {
        KSyntax syntax = findFirstSyntax((KFile) psiReference.getElement().getContainingFile(), sort);
        return syntax != null ? new ResolveResult[]{new PsiElementResolveResult(syntax)} : new ResolveResult[0];
    }

    @NotNull
    public static ResolveResult[] resolveRuleVar(PsiReference psiReference, String name) {
        //The target of a local variable reference is the first typed variable declaration.
        final List<KVarDec> varDecs = findVarDecsInSameRule(psiReference.getElement(), name);
        return varDecs.size() >= 1
                ? new ResolveResult[]{new PsiElementResolveResult(varDecs.get(0))}
                : new ResolveResult[0];
    }
}
