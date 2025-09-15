package land.soia

sealed interface TypeDescriptor

enum class PrimitiveType {
    BOOL,
    INT_32,
    INT_64,
    UINT_64,
    FLOAT_32,
    FLOAT_64,
    TIMESTAMP,
    STRING,
    BYTES,
}

sealed interface PrimitiveDescritor : TypeDescriptor {
    val primitiveType: PrimitiveType
}

interface OptionalDescriptor : TypeDescriptor {
    val otherType: TypeDescriptor
}

/** Describes a list type. */
interface ListDescriptor : TypeDescriptor {
    /** Describes the type of the array items. */
    val itemType: TypeDescriptor
    val keyChain: String?
}

sealed interface RecordDescriptor : TypeDescriptor {
    /** Name of the struct as specified in the `.soia` file. */
    val name: String

    /**
     * A string containing all the names in the hierarchic sequence above and
     * including the struct. For example: "Foo.Bar" if "Bar" is nested within a
     * type called "Foo", or simply "Bar" if "Bar" is defined at the top-level of
     * the module.
     */
    val qualifiedName: String

    /**
     * Path to the module where the struct is defined, relative to the root of the
     * project.
     */
    val modulePath: String

    /**
     * If the struct is nested within another type, the descriptor for that type.
     * Undefined if the struct is defined at the top-level of the module.
     */
    val parentType: RecordDescriptor?

    /** The field numbers marked as removed. */
    val removedNumbers: Set<Int>
}

/** Describes a Soia struct. */
interface StructDescriptor<Frozen : Any, Mutable : Any> : RecordDescriptor {
    /** Field of a Soia struct. */
    interface Field<Frozen : Any, Mutable : Any, Value> {
        /** Field name as specified in the `.soia` file, e.g. "user_id". */
        val name: String

        /** Field number. */
        val number: Int

        /** Describes the field type. */
        val type: TypeDescriptor

        /** Extracts the value of the field from the given struct. */
        fun get(struct: Frozen): Value

        /** Assigns the given value to the field of the given struct. */
        fun set(
            struct: Mutable,
            value: Value,
        )
    }

    /** The fields of the struct in the order they appear in the `.soia` file. */
    val fields: List<Field<Frozen, Mutable, *>>

    /** Looks up a field by name as specified in the `.soia` file, e.g. "user_id". */
    fun getField(name: String): Field<Frozen, Mutable, *>?

    /** Looks up a field by number. */
    fun getField(number: Int): Field<Frozen, Mutable, *>?

    /**
     * Returns a new instance of the generated mutable class for a struct.
     * Performs a shallow copy of `initializer` if `initializer` is specified.
     */
    fun newMutable(initializer: Frozen?): Mutable

    fun toFrozen(mutable: Mutable): Frozen
}

/** Describes a Soia enum. */
interface EnumDescriptor<Enum : Any> : RecordDescriptor {
    sealed interface Field<Enum : Any> {
        val name: String
        val number: Int
    }

    interface ConstantField<Enum : Any> : Field<Enum> {
        val constant: Enum
    }

    interface ValueField<Enum : Any, Value> : Field<Enum> {
        val typeDescriptor: TypeDescriptor

        /** Returns whether the given enum instance if it matches this enum field. */
        fun test(e: Enum): Boolean

        /**
         * Extracts the value held by the given enum instance assuming it matches this
         * enum field. The behavior is undefined if `test(e)` is false.
         */
        fun get(e: Enum): Value

        /**
         * Returns a new enum instance matching this enum field and holding the given
         * value.
         */
        fun wrap(value: Value): Enum
    }

    /** The fields of the enum in the order they appear in the `.soia` file. */
    val fields: List<Field<Enum>>

    /**
     * Looks up a field by name as specified in the `.soia` file, e.g. "RED" or
     * "user_id".
     */
    fun getField(name: String): Field<Enum>?

    /** Looks up a field by number. */
    fun getField(number: Int): Field<Enum>?

    /** Looks up the field corresponding to the given instance of Enum. */
    fun getField(e: Enum): Field<Enum>
}
