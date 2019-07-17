/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.util.SmartList
import org.rust.lang.core.completion.createLookupElement
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.MethodResolveVariant
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.emptySubstitution
import org.rust.lang.core.types.ty.Ty

/**
 * ScopeEntry is some PsiElement visible in some code scope.
 *
 * [ScopeEntry] handles the two case:
 *   * aliases (that's why we need a [name] property)
 *   * lazy resolving of actual elements (that's why [element] can return `null`)
 */
interface ScopeEntry {
    val name: String
    val element: RsElement?
    val subst: Substitution get() = emptySubstitution
}

/**
 * This special event allows to transmit "out of band" information
 * to the resolve processor
 */
enum class ScopeEvent : ScopeEntry {
    /**
     * Communicate to the resolve processor that we are about to process wildecard imports.
     * This is basically a hack to make winapi 0.2 work in a reasonable amount of time.
     *
     * This event brings (very rare) false negatives: an item can be resolved when it
     * should be multiresolved (i.e. unresolved from the rustc view)
     */
    STAR_IMPORTS,

    /**
     * Communicate to the resolve processor that we are changed resolving scope, i.e.
     * we will NOT process elements with names that are already processed.
     * This event doesn't break the name resolution rules (unlike [STAR_IMPORTS]).
     * Used for optimization purposes (mostly optimizes local variables resolution)
     */
    SHADOWING_SCOPE;

    override val element: RsElement? get() = null
}

/**
 * Return `true` to stop further processing,
 * return `false` to continue search
 */
typealias RsResolveProcessor = (ScopeEntry) -> Boolean
typealias RsMethodResolveProcessor = (MethodResolveVariant) -> Boolean

fun collectPathResolveVariants(
    referenceName: String,
    f: (RsResolveProcessor) -> Unit
): List<BoundElement<RsElement>> {
    val result = SmartList<BoundElement<RsElement>>()
    f { e ->
        if ((e == ScopeEvent.STAR_IMPORTS || e == ScopeEvent.SHADOWING_SCOPE) && result.isNotEmpty()) {
            return@f true
        }

        if (e.name == referenceName) {
            val element = e.element ?: return@f false
            result += BoundElement(element, e.subst)
        }
        false
    }
    return result
}

fun collectResolveVariants(referenceName: String, f: (RsResolveProcessor) -> Unit): List<RsElement> {
    val result = SmartList<RsElement>()
    f { e ->
        if ((e == ScopeEvent.STAR_IMPORTS || e == ScopeEvent.SHADOWING_SCOPE) && result.isNotEmpty()) {
            return@f true
        }

        if (e.name == referenceName) {
            result += e.element ?: return@f false
        }
        false
    }
    return result
}

fun <T : ScopeEntry> collectResolveVariantsAsScopeEntries(referenceName: String, f: ((T) -> Boolean) -> Unit): List<T> {
    val result = SmartList<T>()
    f { e ->
        if ((e == ScopeEvent.STAR_IMPORTS || e == ScopeEvent.SHADOWING_SCOPE) && result.isNotEmpty()) {
            return@f true
        }

        if (e.name == referenceName) {
            // de-lazying. See `RsResolveProcessor.lazy`
            e.element ?: return@f false
            result += e
        }
        false
    }
    return result
}

fun pickFirstResolveVariant(referenceName: String, f: (RsResolveProcessor) -> Unit): RsElement? {
    var result: RsElement? = null
    f { e ->
        if (e.name == referenceName) {
            result = e.element
            return@f result != null
        }
        false
    }
    return result
}

fun collectCompletionVariants(
    result: CompletionResultSet,
    forSimplePath: Boolean = false,
    expectedTy: Ty? = null,
    f: (RsResolveProcessor) -> Unit
) {
    f { e ->
        val element = e.element ?: return@f false
        if (element is RsFunction && element.isTest) return@f false
        result.addElement(createLookupElement(
            element = element,
            scopeName = e.name,
            forSimplePath = forSimplePath,
            expectedTy = expectedTy
        ))
        false
    }
}

data class SimpleScopeEntry(
    override val name: String,
    override val element: RsElement,
    override val subst: Substitution = emptySubstitution
) : ScopeEntry

interface AssocItemScopeEntryBase<out T: RsAbstractable> : ScopeEntry {
    override val element: T
    val selfTy: Ty
    val source: TraitImplSource
}

data class AssocItemScopeEntry(
    override val name: String,
    override val element: RsAbstractable,
    override val subst: Substitution = emptySubstitution,
    override val selfTy: Ty,
    override val source: TraitImplSource
) : AssocItemScopeEntryBase<RsAbstractable>

private class LazyScopeEntry(
    override val name: String,
    thunk: Lazy<RsElement?>
) : ScopeEntry {
    override val element: RsElement? by thunk

    override fun toString(): String = "LazyScopeEntry($name, $element)"
}


operator fun RsResolveProcessor.invoke(name: String, e: RsElement): Boolean =
    this(SimpleScopeEntry(name, e))

fun RsResolveProcessor.lazy(name: String, e: () -> RsElement?): Boolean =
    this(LazyScopeEntry(name, lazy(LazyThreadSafetyMode.PUBLICATION, e)))

operator fun RsResolveProcessor.invoke(e: RsNamedElement): Boolean {
    val name = e.name ?: return false
    return this(name, e)
}

operator fun RsResolveProcessor.invoke(e: BoundElement<RsNamedElement>): Boolean {
    val name = e.element.name ?: return false
    return this(SimpleScopeEntry(name, e.element, e.subst))
}

fun processAll(elements: List<RsNamedElement>, processor: RsResolveProcessor): Boolean {
    return elements.any { processor(it) }
}

fun processAllScopeEntries(elements: List<ScopeEntry>, processor: RsResolveProcessor): Boolean {
    return elements.any { processor(it) }
}

fun processAllWithSubst(
    elements: Collection<RsNamedElement>,
    subst: Substitution,
    processor: RsResolveProcessor
): Boolean {
    for (e in elements) {
        if (processor(BoundElement(e, subst))) return true
    }
    return false
}

fun filterCompletionVariantsByVisibility(processor: RsResolveProcessor, mod: RsMod): RsResolveProcessor {
    return fun(it: ScopeEntry): Boolean {
        val element = it.element
        if (element is RsVisible && !element.isVisibleFrom(mod)) return false

        val isHidden = element is RsOuterAttributeOwner && element.queryAttributes.isDocHidden &&
            element.containingMod != mod
        if (isHidden) return false

        return processor(it)
    }
}
