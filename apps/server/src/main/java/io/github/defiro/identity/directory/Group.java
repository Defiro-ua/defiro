package io.github.defiro.identity.directory;

/** A group of the organization, such as «Chefias», or a unit, such as «Divisão Financeira». */
public record Group(String name, Kind kind) {

    public enum Kind {
        GROUP,
        UNIT
    }
}
