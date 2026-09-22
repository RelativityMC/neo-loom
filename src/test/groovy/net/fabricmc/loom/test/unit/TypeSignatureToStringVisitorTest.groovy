/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.test.unit

import org.objectweb.asm.signature.SignatureReader
import org.relativitymc.neoloom.neoforge.util.TypeSignatureToStringVisitor
import spock.lang.Specification

import net.fabricmc.loom.util.Constants

class TypeSignatureToStringVisitorTest extends Specification {

	def "converts type signature #signature to #expected"() {
		given:
		def reader = new SignatureReader(signature)
		def visitor = new TypeSignatureToStringVisitor(Constants.ASM_VERSION)

		when:
		reader.acceptType(visitor)

		then:
		visitor.getString() == expected

		where:
		signature                          | expected
		'Z'                                | 'boolean'
		'C'                                | 'char'
		'B'                                | 'byte'
		'S'                                | 'short'
		'I'                                | 'int'
		'F'                                | 'float'
		'J'                                | 'long'
		'D'                                | 'double'
		'V'                                | 'void'
		'Lsome/Type;'                      | 'some.Type'
		'TT;'                              | 'T'
		'Lsome/Type<TT;D>;'                | 'some.Type<T, double>'
		'Lsome/Type<TT;TD;>;'              | 'some.Type<T, D>'
		'Lsome/Type<Lother/Type<TT;>;>;'   | 'some.Type<other.Type<T>>'
		'Lsome/Type<*>;'                   | 'some.Type<?>'
		'Lsome/Type<+Ljava/lang/Number;>;' | 'some.Type<? extends java.lang.Number>'
		'Lsome/Type<-Ljava/lang/Number;>;' | 'some.Type<? super java.lang.Number>'
		'[I'                               | 'int[]'
		'[[D'                              | 'double[][]'
		'[Lsome/Type<TT;>;'                | 'some.Type<T>[]'
		'Lsome/Outer$Inner;'               | 'some.Outer$Inner'
		'Lsome/Outer<TT;>.Inner<TD;>;'     | 'some.Outer<T>.Inner<D>'
	}
}
