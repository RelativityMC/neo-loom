/*
 * InstallerTools
 * Copyright (c) 2019-2025.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.relativitymc.neoloom.neoforge.modmeta;

import org.objectweb.asm.signature.SignatureWriter;

/**
 * From <a href="https://github.com/neoforged/InstallerTools/blob/1a089bce191cfd77cef2484d61f27210014a1f2d/src/main/java/net/neoforged/installertools/SuperinterfaceSignatureConverter.java">InstallerTools SuperinterfaceSignatureConverter</a>.
 */
public class NeoForgeInterfaceInjectionUtils {
	public static String neoForge2ClassTweaker(String iface) {
		SignatureWriter writer = new SignatureWriter();
		writeInterfaceSignature(removeWhitespace(iface), writer);
		String fullSignature = writer.toString();
		return fullSignature.startsWith("L") && fullSignature.endsWith(";") ? fullSignature.substring(1, fullSignature.length() - 1) : fullSignature;
	}

	public static String removeWhitespace(String iface) {
		return iface.replaceAll("\\s", "");
	}

	public static void writeInterfaceSignature(String iface, SignatureWriter writer) {
		int genericStart = iface.indexOf('<');
		int genericEnd = iface.lastIndexOf('>');

		if (genericStart == -1) {
			writer.visitInterface();
			writer.visitClassType(iface);
			writer.visitEnd();
		} else {
			writer.visitInterface();
			writer.visitClassType(iface.substring(0, genericStart));
			String generics = iface.substring(genericStart + 1, genericEnd).replace('.', '/');

			for (String param : splitGenerics(generics)) {
				writeTypeArgument(param.trim(), writer);
			}

			writer.visitEnd();
		}
	}

	private static String[] splitGenerics(String generics) {
		java.util.List<String> result = new java.util.ArrayList<>();
		int depth = 0;
		StringBuilder current = new StringBuilder();

		for (char c : generics.toCharArray()) {
			if (c == '<') depth++;
			if (c == '>') depth--;

			if (c == ',' && depth == 0) {
				result.add(current.toString());
				current.setLength(0);
			} else {
				current.append(c);
			}
		}

		if (current.length() > 0) result.add(current.toString());
		return result.toArray(new String[0]);
	}

	private static void writeTypeArgument(String param, SignatureWriter sw) {
		// Wildcards are supported for nested generics, so let's always write them
		if (param.startsWith("?super")) {
			sw.visitTypeArgument('-');
			param = param.substring(6);
		} else if (param.startsWith("?extends")) {
			sw.visitTypeArgument('+');
			param = param.substring(8);
		} else {
			sw.visitTypeArgument('=');
		}

		writeType(param, sw);
	}

	private static void writeType(String type, SignatureWriter sw) {
		int genericStart = type.indexOf('<');
		int genericEnd = type.lastIndexOf('>');

		if (genericStart == -1) {
			if (type.contains("/")) {
				sw.visitClassType(type);
				sw.visitEnd();
			} else {
				sw.visitTypeVariable(type);
			}
		} else {
			String base = type.substring(0, genericStart);
			sw.visitClassType(base);

			String generics = type.substring(genericStart + 1, genericEnd);

			for (String param : splitGenerics(generics)) {
				writeTypeArgument(param.trim(), sw);
			}

			sw.visitEnd();
		}
	}
}
