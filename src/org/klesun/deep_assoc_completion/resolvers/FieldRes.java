package org.klesun.deep_assoc_completion.resolvers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.Function;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpExpression;
import com.jetbrains.php.lang.psi.elements.impl.*;
import org.klesun.deep_assoc_completion.*;
import org.klesun.deep_assoc_completion.helpers.FuncCtx;
import org.klesun.deep_assoc_completion.helpers.MultiType;
import org.klesun.deep_assoc_completion.resolvers.var_res.AssRes;
import org.klesun.deep_assoc_completion.resolvers.var_res.DocParamRes;
import org.klesun.lang.Lang;
import org.klesun.lang.Opt;
import org.klesun.lang.Tls;

import java.util.List;

public class FieldRes extends Lang
{
    final private FuncCtx ctx;

    public FieldRes(FuncCtx ctx)
    {
        this.ctx = ctx;
    }

    private static L<FieldReferenceImpl> findReferences(PsiFile file, String name)
    {
        // ReferenceSearch seems to cause freezes
//        SearchScope scope = GlobalSearchScope.fileScope(
//            fieldRef.getProject(),
//            decl.getContainingFile().getVirtualFile()
//        );
//        return ReferencesSearch.search(decl, scope, false).findAll();

        return L(PsiTreeUtil.findChildrenOfType(file, FieldReferenceImpl.class))
            .flt(ref -> name.equals(ref.getName()));
    }

    private static boolean areInSameScope(PsiElement a, PsiElement b)
    {
        Opt<Function> aFunc = Tls.findParent(a, Function.class, v -> true);
        Opt<Function> bFunc = Tls.findParent(b, Function.class, v -> true);
        return aFunc.equals(bFunc);
    }

    private

    public MultiType resolve(FieldReferenceImpl fieldRef)
    {
        L<DeepType> result = list();
        S<MultiType> getObjMt = Tls.onDemand(() -> opt(fieldRef.getClassReference())
            .fop(ref -> Opt.fst(
                () -> ctx.clsIdeaType
                    .flt(typ -> ref.getText().equals("static"))
                    .flt(typ -> ArrCtorRes.resolveIdeaTypeCls(typ, ref.getProject()).size() > 0)
                    .map(typ -> new MultiType(list(new DeepType(ref, typ)))),
                () -> opt(ctx.findExprType(ref))
            ))
            .def(MultiType.INVALID_PSI));

        L<Field> declarations = Opt.fst(
            () -> L(fieldRef.multiResolve(false))
                .map(reses -> reses.map(res -> res.getElement())),
            () -> opt(getObjMt.get())
                .fop(mt -> ArrCtorRes.resolveMtCls(mt, fieldRef.getProject()))
                .fap(cls -> L(cls.getFields()))
                .flt(f -> f.getName().equals(fieldRef.getName()))
        ).fap(a -> a);

        ArrCtorRes.resolveMtCls(getObjMt, fieldRef.getProject())
            .fap(cls -> L(cls.getFields()))
            .flt(f -> f.getName().equals(fieldRef.getName()))
            .fch(resolved -> {
                FuncCtx implCtx = new FuncCtx(ctx.getSearch());
                Tls.cast(FieldImpl.class, resolved)
                    .map(fld -> fld.getDefaultValue())
                    .fop(toCast(PhpExpression.class))
                    .map(def -> implCtx.findExprType(def).types)
                    .thn(result::addAll);

                L<Assign> docAsses = opt(resolved.getContainingClass())
                    .map(cls -> cls.getDocComment())
                    .fap(doc -> L(doc.getPropertyTags()))
                    .flt(tag -> opt(tag.getProperty()).flt(pro -> pro.getName().equals(fieldRef.getName())).has())
                    .map(tag -> {
                        S<MultiType> getMt = () -> new DocParamRes(ctx).resolve(tag).def(MultiType.INVALID_PSI);
                        return new Assign(list(), getMt, true, tag, tag.getType());
                    })
                    ;

                System.out.println("zhopa " + fieldRef.getText());
                System.out.println("pizda " + Tls.implode("; ", docAsses.map(ass -> ass.assignedType.get() + "")));

                L<Assign> asses = opt(resolved.getContainingFile())
                    .map(file -> findReferences(file, fieldRef.getName()))
                    .def(L())
                    .fap(assPsi -> Tls.findParent(assPsi, Method.class, a -> true)
                        .flt(meth -> meth.getName().equals("__construct"))
                        .map(meth -> fieldRef.getClassReference())
                        .fop(toCast(PhpExpression.class))
                        .fop(ref -> opt(ctx.findExprType(ref)))
                        .fap(mt -> mt.getArgsPassedToCtor())
                        .wap(ctxs -> {
                            if (areInSameScope(fieldRef, assPsi)) {
                                ctxs.add(ctx);
                            }
                            if (ctxs.size() == 0) {
                                ctxs = list(implCtx);
                            }
                            return ctxs;
                        })
                        .fop(methCtx -> (new AssRes(methCtx)).collectAssignment(assPsi, false)));

                List<DeepType> types = AssRes.assignmentsToTypes(docAsses.cct(asses));
                result.addAll(types);
            });

        getObjMt.getProps()
            .flt(prop -> prop.name.equals(fieldRef.getName()))
            .fch(prop -> result.addAll(prop.getTypes()));

        return new MultiType(result);
    }

}
