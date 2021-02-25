package rise.openCL

import rise.core.TypeLevelDSL._
import rise.core.types.{AddressSpace, DataType, IndexType, Nat, NatCollection, NatCollectionToData, NatToData, NatType, bool}
import rise.core.{Builder, Primitive}
import rise.macros.Primitive.primitive

// noinspection DuplicatedCode
object primitives {
  // TODO? depMapGlobal, depMapLocal, depMapWorkGroup

  @primitive case class mapGlobal(dim: Int) extends Primitive with Builder {
    impl{ n: Nat => impl{ s: DataType => impl{ t: DataType =>
      (s ->: t) ->: (n`.`s) ->: (n`.`t) }}}
  }

  @primitive case class mapLocal(dim: Int) extends Primitive with Builder {
    impl{ n: Nat => impl{ s: DataType => impl{ t: DataType =>
      (s ->: t) ->: (n`.`s) ->: (n`.`t) }}}
  }

  @primitive case class mapWorkGroup(dim: Int) extends Primitive with Builder {
    impl{ n: Nat => impl{ s: DataType => impl{ t: DataType =>
      (s ->: t) ->: (n`.`s) ->: (n`.`t) }}}
  }


  @primitive case class depMapGlobal(dim: Int) extends Primitive with Builder {
    impl{ n: Nat =>
      impl{ ft1: NatToData => impl{ ft2: NatToData =>
        expl((k: Nat) => ft1(k) ->: ft2(k)) ->: (n`*.`ft1) ->: (n`*.`ft2)}}}
  }

  @primitive case class depMapWorkgroup(dim: Int) extends Primitive with Builder {
    impl{ n: Nat =>
      impl{ ft1: NatToData => impl{ ft2: NatToData =>
        expl((k: Nat) => ft1(k) ->: ft2(k)) ->: (n`*.`ft1) ->: (n`*.`ft2)}}}
  }


  @primitive object oclToMem extends Primitive with Builder {
    impl{ t: DataType => expl((_: AddressSpace) => t ->: t) }
  }

  @primitive object oclReduceSeq extends Primitive with Builder {
    expl((_: AddressSpace) =>
      impl{ n: Nat => impl{ s: DataType => impl{ t: DataType =>
        (t ->: s ->: t) ->: t ->: (n`.`s) ->: t }}})
  }

  @primitive object oclReduceSeqUnroll extends Primitive with Builder {
    expl((_: AddressSpace) =>
      impl{ n: Nat => impl{ s: DataType => impl{ t: DataType =>
        (t ->: s ->: t) ->: t ->: (n`.`s) ->: t }}})
  }

  @primitive object oclScanSeq extends Primitive with Builder {
    expl((_: AddressSpace) => impl{ n: Nat => impl{ s: DataType => impl{ t: DataType =>
      (s ->: t ->: t) ->: t ->: (n `.` s) ->: (n `.` t) }}}
    )
  }

  @primitive object oclScanSeqInclusive extends Primitive with Builder {
    expl((_: AddressSpace) => impl{ n: Nat => impl{ s: DataType => impl{ t: DataType =>
      (s ->: t ->: t) ->: t ->: (n `.` s) ->: ((n+1) `.` t) }}}
    )
  }

  @primitive object oclIterate extends Primitive with Builder {
    expl((_: AddressSpace) =>
      impl{ n: Nat => impl{ m: Nat =>
        expl((k: Nat) => impl{ t: DataType =>
          expl((l: Nat) => ((l * n)`.`t) ->: (l`.`t)) ->:
            ((m * n.pow(k))`.`t) ->: (m`.`t) })}})
  }

  @primitive object oclCount extends Primitive with Builder {
    expl((_: AddressSpace) =>
      impl { n: Nat => (n `.` bool)  ->: IndexType(n) }
    )
  }

  @primitive object oclWhich extends Primitive with Builder {
    impl { n: Nat =>  (n `.` bool) ->: expl((count: Nat) => count `.` IndexType(n))}
  }

  @primitive object oclWhichMap extends Primitive with Builder {
    impl { n: Nat => impl { dt: DataType => (n `.` bool) ->: (IndexType(n) ->: dt) ->: expl((count: Nat) => count `.` dt) } }
  }

  @primitive object oclDpairNats extends Primitive with Builder {
    expl { (ns: NatCollection) => impl { fdt: NatCollectionToData =>
      expl { (n:Nat) => (n `.` NatType) ->: (n `.` NatType)} ->: fdt(ns) ->: (NatCollection `**` fdt)
    } }
  }


  @primitive object oclCircularBuffer extends Primitive with Builder {
    // TODO: should return a stream / sequential array, not an array
    expl((_: AddressSpace) => impl{ n: Nat =>
      expl((alloc: Nat) => expl((sz: Nat) =>
        impl{ s: DataType => impl{ t: DataType =>
          (s ->: t) ->: // function to load an input
            ((n + sz)`.`s) ->: ((1 + n)`.`sz`.`t) }}))})
  }

  @primitive object oclRotateValues extends Primitive with Builder {
    // TODO: should return a stream / sequential array, not an array
    expl((_: AddressSpace) => impl{ n: Nat =>
      expl((sz: Nat) => impl{ s: DataType =>
        (s ->: s) ->: // function to write a value
          ((n + sz)`.`s) ->: ((1 + n)`.`sz`.`s) })})
  }
}
