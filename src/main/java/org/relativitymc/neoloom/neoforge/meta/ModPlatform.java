package org.relativitymc.neoloom.neoforge.meta;

public enum ModPlatform {
	FABRIC(false),
	NEOFORGE(true),
	;

	private final boolean isForgeLike;

	ModPlatform(boolean isForgeLike) {
		this.isForgeLike = isForgeLike;
	}

	public boolean isForgeLike() {
		return this.isForgeLike;
	}
}
