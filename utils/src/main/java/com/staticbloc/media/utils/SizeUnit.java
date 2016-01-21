package com.staticbloc.media.utils;

public enum SizeUnit {
  BITS {
    public long toBits(long d) { return d; }
    public long toBytes(long d) { return d/(C1/C0); }
    public long toKb(long d) { return d/(C2/C0); }
    public long toMb(long d) { return d/(C3/C0); }
    public long toGb(long d) { return d/(C4/C0); }
    public long convert(long d, SizeUnit u) { return u.toBits(d); }
  },

  BYTES {
    public long toBits(long d) { return x(d, C1/C0, MAX/(C1/C0)); }
    public long toBytes(long d) { return d; }
    public long toKb(long d) { return d/(C2/C1); }
    public long toMb(long d) { return d/(C3/C1); }
    public long toGb(long d) { return d/(C4/C1); }
    public long convert(long d, SizeUnit u) { return u.toBytes(d); }
  },

  KB {
    public long toBits(long d) { return x(d, C2/C0, MAX/(C2/C0)); }
    public long toBytes(long d) { return x(d, C2/C1, MAX/(C2/C1)); }
    public long toKb(long d) { return d; }
    public long toMb(long d) { return d/(C3/C2); }
    public long toGb(long d) { return d/(C4/C2); }
    public long convert(long d, SizeUnit u) { return u.toKb(d); }
  },

  MB {
    public long toBits(long d) { return x(d, C3/C0, MAX/(C3/C0)); }
    public long toBytes(long d) { return x(d, C3/C1, MAX/(C3/C1)); }
    public long toKb(long d) { return x(d, C3/C2, MAX/(C3/C2)); }
    public long toMb(long d) { return d; }
    public long toGb(long d) { return d/(C4/C3); }
    public long convert(long d, SizeUnit u) { return u.toMb(d); }
  },

  GB {
    public long toBits(long d) { return x(d, C4/C0, MAX/(C4/C0)); }
    public long toBytes(long d) { return x(d, C4/C1, MAX/(C4/C1)); }
    public long toKb(long d) { return x(d, C4/C2, MAX/(C4/C2)); }
    public long toMb(long d) { return x(d, C4/C3, MAX/(C4/C3)); }
    public long toGb(long d) { return d; }
    public long convert(long d, SizeUnit u) { return u.toGb(d); }
  };

  private static long C0 = 1L;
  private static long C1 = C0 * 8L;
  private static long C2 = C1 * 1000L;
  private static long C3 = C2 * 1000L;
  private static long C4 = C3 * 1000L;

  private static long MAX = Long.MAX_VALUE;

  static long x(long d, long m, long over) {
    if (d >  over) return Long.MAX_VALUE;
    if (d < -over) return Long.MIN_VALUE;
    return d * m;
  }

  public long toBits(long d) { throw new AbstractMethodError(); }
  public long toBytes(long d) { throw new AbstractMethodError(); }
  public long toKb(long d) { throw new AbstractMethodError(); }
  public long toMb(long d) { throw new AbstractMethodError(); }
  public long toGb(long d) { throw new AbstractMethodError(); }
  public long convert(long d, SizeUnit u) { throw new AbstractMethodError(); }
  }
