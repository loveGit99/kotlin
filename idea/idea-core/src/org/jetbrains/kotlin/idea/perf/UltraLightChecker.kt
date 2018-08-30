/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.perf

import com.intellij.psi.*
import junit.framework.TestCase
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.asJava.classes.KtLightClassForSourceDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert

@TestOnly
object UltraLightChecker {
    /**
     * @return true if loaded cls
     */
    public fun checkClassEquivalence(file: KtFile, clsLoadingExpected: Boolean?): Boolean {
        var loadedCls = false
        val ktClasses = file.declarations.filterIsInstance<KtClassOrObject>().toList()
        val goldText = ktClasses.joinToString("\n\n") {
            val gold = KtLightClassForSourceDeclaration.create(it)
            if (gold != null) {
                Assert.assertFalse(gold.javaClass.name.contains("Ultra"))
            }
            gold?.render().orEmpty()
        }
        val newText = ktClasses.joinToString("\n\n") {
            val clazz = KtLightClassForSourceDeclaration.createUltraLight(it)
            if (clazz != null) {
                val result = clazz.render()
                if (clazz.isClsDelegateLoaded) {
                    loadedCls = true
                }
                if (clsLoadingExpected == false && clazz.isClsDelegateLoaded) {
                    TestCase.fail("Cls delegate isn't expected to be loaded!")
                }
                result
            } else ""
        }
        if (goldText != newText) {
            println(file.virtualFilePath)
            Assert.assertEquals(
                "//Classic implementation:\n$goldText",
                "//Light implementation:\n$newText"
            )
        }
        if (clsLoadingExpected == true && !loadedCls) {
            TestCase.fail("mayLoadCls should be false")
        }
        return loadedCls
    }

    private fun PsiClass.render(): String {
        fun PsiAnnotation.renderAnnotation() =
            "@" + qualifiedName + "(" + parameterList.attributes.joinToString { it.name + "=" + (it.value?.text ?: "?") } + ")"

        fun PsiModifierListOwner.renderModifiers() =
            annotations.joinToString("") { it.renderAnnotation() + (if (this is PsiParameter) " " else "\n") } +
                    PsiModifier.MODIFIERS.filter(::hasModifierProperty).joinToString("") { "$it " }

        fun PsiType.renderType() = getCanonicalText(true)

        fun PsiReferenceList?.renderRefList(keyword: String): String {
            if (this == null || this.referencedTypes.isEmpty()) return ""
            return " " + keyword + " " + referencedTypes.joinToString { it.renderType() }
        }

        fun PsiVariable.renderVar(): String {
            var result = this.renderModifiers() + type.renderType() + " " + name
            if (this is PsiParameter && this.isVarArgs) {
                result += " /* vararg */"
            }
            computeConstantValue()?.let { result += " /* constant value $it */" }
            return result
        }

        fun PsiTypeParameterListOwner.renderTypeParams() =
            if (typeParameters.isEmpty()) ""
            else "<" + typeParameters.joinToString {
                val bounds =
                    if (it.extendsListTypes.isNotEmpty())
                        " extends " + it.extendsListTypes.joinToString(" & ", transform = PsiClassType::renderType)
                    else ""
                it.name!! + bounds
            } + "> "

        fun PsiMethod.renderMethod() =
            renderModifiers() +
                    (if (isVarArgs) "/* vararg */ " else "") +
                    renderTypeParams() +
                    (returnType?.renderType() ?: "") + " " +
                    name +
                    "(" + parameterList.parameters.joinToString { it.renderModifiers() + it.type.renderType() } + ")" +
                    (this as? PsiAnnotationMethod)?.defaultValue?.let { " default " + it.text }.orEmpty() +
                    throwsList.referencedTypes.let { thrownTypes ->
                        if (thrownTypes.isEmpty()) ""
                        else " throws " + thrownTypes.joinToString { it.renderType() }
                    } +
                    ";"

        val classWord = when {
            isAnnotationType -> "@interface"
            isInterface -> "interface"
            isEnum -> "enum"
            else -> "class"
        }

        return renderModifiers() +
                classWord + " " +
                name + " /* " + qualifiedName + "*/" +
                renderTypeParams() +
                extendsList.renderRefList("extends") +
                implementsList.renderRefList("implements") +
                " {\n" +
                (if (isEnum) fields.filterIsInstance<PsiEnumConstant>().joinToString(",\n") { it.name } + ";\n\n" else "") +
                fields.filterNot { it is PsiEnumConstant }.map { it.renderVar().prependIndent("  ") + ";\n\n" }.sorted().joinToString("") +
                methods.map { it.renderMethod().prependIndent("  ") + "\n\n" }.sorted().joinToString("") +
                innerClasses.map { it.render().prependIndent("  ") }.sorted().joinToString("") +
                "}"
    }

}