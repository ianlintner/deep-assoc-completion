package org.klesun.deep_assoc_completion.entry;

import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import com.jetbrains.php.injection.PhpElementPattern;
import com.jetbrains.php.lang.parser.PhpElementTypes;
import com.jetbrains.php.lang.psi.PhpPsiUtil;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.PhpPsiElement;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import dk.brics.automaton.Automaton;
import dk.brics.automaton.DatatypesAutomatonProvider;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PhpPatterns2 extends PlatformPatterns
{
    private static final int STRING_LITERAL_LIMIT = 10000;

    public static PhpElementPattern phpElement() {
        return new PhpElementPattern.Capture(PhpPsiElement.class);
    }

    public static PhpElementPattern phpFunctionReference() {
        return new PhpElementPattern.Capture(FunctionReference.class);
    }

    public static PhpElementPattern.Capture phpLiteralExpression() {
        return phpLiteralExpression((String)null);
    }

    public static PhpElementPattern.Capture phpLiteralMatchesBrics(@NotNull String pattern) {
        StringBuilder sb = new StringBuilder(pattern.length() * 5);

        for(int i = 0; i < pattern.length(); ++i) {
            char c = pattern.charAt(i);
            if (c == ' ') {
                sb.append("<whitespacechar>");
            } else if (Character.isUpperCase(c)) {
                sb.append('[').append(Character.toUpperCase(c)).append(Character.toLowerCase(c)).append(']');
            } else {
                sb.append(c);
            }
        }

        RegExp regExp = new RegExp(sb.toString());
        Automaton automaton = regExp.toAutomaton(new DatatypesAutomatonProvider());
        final RunAutomaton runAutomaton = new RunAutomaton(automaton, true);
        return new PhpElementPattern.Capture(new InitialPatternCondition(StringLiteralExpression.class) {
            public boolean accepts(@Nullable Object o, ProcessingContext context) {
                if (o instanceof StringLiteralExpression) {
                    StringLiteralExpression expr = (StringLiteralExpression)o;
                    if (expr.getTextLength() < 10000) {
                        String value = expr.getContents();
                        boolean run = runAutomaton.run(value);
                        return run;
                    }
                }

                return false;
            }
        });
    }

    public static PhpElementPattern.Capture phpLiteralExpression(final String name) {
        return new PhpElementPattern.Capture(new InitialPatternCondition(StringLiteralExpression.class) {
            public boolean accepts(@Nullable Object o, ProcessingContext context) {
                if (o instanceof StringLiteralExpression) {
                    StringLiteralExpression s = (StringLiteralExpression)o;
                    if ((!PhpPsiUtil.isOfType(s.getParent(), PhpElementTypes.CONCATENATION_EXPRESSION) || s.getPrevPsiSibling() == null) && (name == null && s.getName() == null || name != null && name.equalsIgnoreCase(s.getName()))) {
                        return true;
                    }
                }

                return false;
            }
        });
    }
}
