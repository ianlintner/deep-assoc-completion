package org.klesun.deep_assoc_completion.completion_providers;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.elements.impl.BinaryExpressionImpl;
import com.jetbrains.php.lang.psi.elements.impl.StringLiteralExpressionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.klesun.deep_assoc_completion.helpers.FuncCtx;
import org.klesun.deep_assoc_completion.helpers.MultiType;
import org.klesun.deep_assoc_completion.helpers.SearchContext;
import org.klesun.lang.Lang;
import org.klesun.lang.Opt;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.klesun.lang.Lang.*;

// string literal after `==` like in `$writeSsrRecords[0]['type'] === ''`
// should suggest possible values of 'type'
public class EqStrValsPvdr extends CompletionProvider<CompletionParameters> implements GotoDeclarationHandler
{
    private static LookupElement makeLookupBase(String keyName, String type)
    {
        return LookupElementBuilder.create(keyName)
                .bold()
                .withIcon(DeepKeysPvdr.getIcon())
                .withTypeText(type);
    }

    private static Lang.L<LookupElement> makeOptions(MultiType mt)
    {
        return mt.getStringValues().map(strVal -> makeLookupBase(strVal, "string"));
    }

    /** $type === '' */
    private static Opt<MultiType> resolveEqExpr(StringLiteralExpression lit, FuncCtx funcCtx)
    {
        return opt(lit)
            .map(literal -> literal.getParent()) // BinaryExpressionImpl
            .fop(toCast(BinaryExpressionImpl.class))
            .fop(bin -> opt(bin.getOperation())
                .flt(op -> op.getText().equals("==") || op.getText().equals("===")
                        || op.getText().equals("!=") || op.getText().equals("!=="))
                .map(op -> bin.getLeftOperand())
                .fop(toCast(PhpExpression.class))
                .map(exp -> funcCtx.findExprType(exp)))
            ;
    }

    /** in_array($type, ['']) */
    private static Opt<MultiType> resolveInArrayHaystack(StringLiteralExpression lit, FuncCtx funcCtx)
    {
        return opt(lit)
            .map(literal -> literal.getParent()) // array value
            .map(literal -> literal.getParent())
            .fop(toCast(ArrayCreationExpression.class))
            .fop(arr -> opt(arr.getParent())
                .fop(toCast(ParameterList.class))
                .flt(lst -> L(lst.getParameters()).gat(1)
                    .flt(arg -> arg.isEquivalentTo(arr)).has()
                )
                .flt(lst -> opt(lst.getParent())
                    .fop(toCast(FunctionReference.class))
                    .map(fun -> fun.getName())
                    .flt(nme -> nme.equals("in_array")).has())
                .fop(lst -> L(lst.getParameters()).gat(0))
                .fop(toCast(PhpExpression.class))
                .map(str -> funcCtx.findExprType(str)));
    }

    /** in_array('', $types) */
    private static Opt<MultiType> resolveInArrayNeedle(StringLiteralExpression lit, FuncCtx funcCtx)
    {
        return opt(lit.getParent())
            .fop(toCast(ParameterList.class))
            .flt(lst -> L(lst.getParameters()).gat(0)
                .flt(arg -> arg.isEquivalentTo(lit)).has())
            .flt(lst -> opt(lst.getParent())
                .fop(toCast(FunctionReference.class))
                .map(fun -> fun.getName())
                .flt(nme -> nme.equals("in_array")).has())
            .fop(lst -> L(lst.getParameters()).gat(1))
            .fop(toCast(PhpExpression.class))
            .map(str -> funcCtx.findExprType(str).getEl());
    }

    // array_intersect($cmdTypes, ['redisplayPnr', 'itinerary', 'airItinerary', 'storeKeepPnr', 'changeArea', ''])
    private static Opt<MultiType> resolveArrayIntersect(StringLiteralExpression lit, FuncCtx funcCtx)
    {
        return opt(lit)
            .map(literal -> literal.getParent()) // array value
            .map(literal -> literal.getParent())
            .fop(toCast(ArrayCreationExpression.class))
            .fop(arr -> opt(arr.getParent())
                .fop(toCast(ParameterList.class))
                .flt(lst -> opt(lst.getParent())
                    .fop(toCast(FunctionReference.class))
                    .map(fun -> fun.getName())
                    .flt(nme -> nme.equals("array_intersect")).has())
                .fap(lst -> L(lst.getParameters()))
                .flt(par -> !arr.isEquivalentTo(par))
                .fop(toCast(PhpExpression.class))
                .map(str -> funcCtx.findExprType(str).getEl())
                .fap(mt -> mt.types)
                .wap(types -> types.size() == 0 ? opt(null) : opt(new MultiType(types))));
    }

    private MultiType resolve(StringLiteralExpression lit, boolean isAutoPopup, Editor editor)
    {
        SearchContext search = new SearchContext(lit.getProject())
            .setDepth(DeepKeysPvdr.getMaxDepth(isAutoPopup, editor.getProject()));
        FuncCtx funcCtx = new FuncCtx(search);

        return Opt.fst(
            () -> resolveEqExpr(lit, funcCtx),
            () -> resolveInArrayHaystack(lit, funcCtx),
            () -> resolveInArrayNeedle(lit, funcCtx),
            () -> resolveArrayIntersect(lit, funcCtx)
        ).def(MultiType.INVALID_PSI);
    }

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext processingContext, @NotNull CompletionResultSet result)
    {
        long startTime = System.nanoTime();
        MultiType mt = opt(parameters.getPosition().getParent()) // StringLiteralExpressionImpl
            .fop(toCast(StringLiteralExpression.class))
            .map(lit -> resolve(lit, parameters.isAutoPopup(), parameters.getEditor()))
            .def(MultiType.INVALID_PSI);

        makeOptions(mt).fch(result::addElement);
        long elapsed = System.nanoTime() - startTime;
        result.addLookupAdvertisement("Resolved in " + (elapsed / 1000000000.0) + " seconds");
    }

    // ================================
    //  GotoDeclarationHandler part follows
    // ================================

    // just treating a symptom. i dunno why duplicates appear - they should not
    private static void removeDuplicates(List<PsiElement> psiTargets)
    {
        Set<PsiElement> fingerprints = new HashSet<>();
        int size = psiTargets.size();
        for (int k = size - 1; k >= 0; --k) {
            if (fingerprints.contains(psiTargets.get(k))) {
                psiTargets.remove(k);
            }
            fingerprints.add(psiTargets.get(k));
        }
    }

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement psiElement, int i, Editor editor) {

        L<PsiElement> psiTargets = opt(psiElement)
            .map(psi -> psi.getParent())
            .fop(toCast(StringLiteralExpressionImpl.class))
            .map(lit -> resolve(lit, false, editor)
                .types.flt(t -> lit.getContents().equals(t.stringValue))
                .map(t -> t.definition))
            .def(list());

        removeDuplicates(psiTargets);

        return psiTargets.toArray(new PsiElement[psiTargets.size()]);
    }

    @Nullable
    @Override
    public String getActionText(DataContext dataContext) {
        // renames the "Declaration" action if returned value is not null
        return null;
    }
}
