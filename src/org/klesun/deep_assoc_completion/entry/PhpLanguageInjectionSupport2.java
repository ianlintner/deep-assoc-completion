package org.klesun.deep_assoc_completion.entry;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.jetbrains.php.lang.patterns.PhpPatterns;
import com.jetbrains.php.lang.psi.elements.PhpPsiElement;
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PhpLanguageInjectionSupport2 extends AbstractLanguageInjectionSupport {
    @NonNls
    private static final String SUPPORT_ID = "php";

    @NotNull
    public String getId() {
        return "php";
    }

    @NotNull
    public Class[] getPatternClasses() {
        return new Class[]{PhpPatterns2.class};
    }

    public boolean isApplicableTo(PsiLanguageInjectionHost host) {
        return host instanceof PhpPsiElement;
    }

    @Nullable
    public BaseInjection findCommentInjection(@NotNull PsiElement host, @Nullable Ref commentRef) {
        return super.findCommentInjection(host, commentRef);
    }

    public String getHelpId() {
        return "reference.settings.injection.language.injection.settings.generic.php";
    }
}
