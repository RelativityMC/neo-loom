package org.relativitymc.neoloom.neoforge.util;

import net.fabricmc.loom.util.Checksum;

import java.util.Locale;

public class NameUtil {
	private NameUtil() {
	}

	public static String mangleLaunchEnvName(String env) {
		if ("server".equals(env) || "client".equals(env)) {
			return env;
		}

		return (Checksum.of(env).sha1().hex(12) + "_" + env).toLowerCase(Locale.ROOT);
	}
}
