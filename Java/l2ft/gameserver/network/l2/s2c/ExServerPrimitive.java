package l2ft.gameserver.network.l2.s2c;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import l2ft.gameserver.network.l2.s2c.L2GameServerPacket;

/**
 * A packet used to draw points and lines on the client (High Five).
 * All fields use (int x, int y, int z) directly.
 *
 * If your client is not in developer mode, you might not see these lines.
 * The naming in lines can appear even when not looking at them (a known issue).
 */
public class ExServerPrimitive extends L2GameServerPacket {
	private final String _name;
	private final int _centerX;
	private final int _centerY;
	private final int _centerZ;

	// [FIX for Java 1.7] Remove diamond operators:
	private final List<Point> _points = new ArrayList<Point>();
	private final List<Line> _lines = new ArrayList<Line>();

	/**
	 * Creates a new geometry debug packet with a "center" coordinate.
	 * 
	 * @param name a unique name to identify this geometry set
	 * @param x    center X
	 * @param y    center Y
	 * @param z    center Z
	 */
	public ExServerPrimitive(String name, int x, int y, int z) {
		_name = name;
		_centerX = x;
		_centerY = y;
		_centerZ = z;
	}

	// ------------------------------------------------------------------------
	// Add a point

	public void addPoint(String name, Color color, boolean isNameColored, int x, int y, int z) {
		int rgb = (color != null) ? color.getRGB() : 0xFFFFFF;
		_points.add(new Point(name, rgb, isNameColored, x, y, z));
	}

	public void addPoint(Color color, int x, int y, int z) {
		addPoint("", color, false, x, y, z);
	}

	// ------------------------------------------------------------------------
	// Add a line

	public void addLine(String name, Color color, boolean isNameColored,
			int x1, int y1, int z1,
			int x2, int y2, int z2) {
		int rgb = (color != null) ? color.getRGB() : 0xFFFFFF;
		_lines.add(new Line(name, rgb, isNameColored, x1, y1, z1, x2, y2, z2));
	}

	public void addLine(Color color, int x1, int y1, int z1,
			int x2, int y2, int z2) {
		addLine("", color, false, x1, y1, z1, x2, y2, z2);
	}

	/**
	 * Optional convenience: add a circle with `segments` line segments.
	 */
	public void addCircle(Color color, int radius, int segments, int zFix) {
		double step = 2 * Math.PI / segments;
		for (int i = 0; i < segments; i++) {
			double angle1 = i * step;
			double angle2 = (i + 1) * step;

			int x1 = _centerX + (int) (radius * Math.cos(angle1));
			int y1 = _centerY + (int) (radius * Math.sin(angle1));
			int x2 = _centerX + (int) (radius * Math.cos(angle2));
			int y2 = _centerY + (int) (radius * Math.sin(angle2));

			addLine("", color, false, x1, y1, zFix, x2, y2, zFix);
		}
	}

	// ------------------------------------------------------------------------
	// Write
	@Override
	protected final void writeImpl() {
		writeEx(0x11); // packet id
		writeS(_name); // geometry name
		writeD(_centerX);
		writeD(_centerY);
		writeD(_centerZ);
		writeD(65535); // might be display range
		writeD(65535); // might be angle or similar

		// total "commands" = points + lines
		writeD(_points.size() + _lines.size());

		// 1) points
		for (Point pt : _points) {
			writeC(1); // type = point
			writeS(pt.getName());
			int c = pt.getColor();
			int r = (c >> 16) & 0xFF;
			int g = (c >> 8) & 0xFF;
			int b = (c) & 0xFF;
			writeD(r);
			writeD(g);
			writeD(b);
			writeD(pt.isNameColored() ? 1 : 0);
			writeD(pt.getX());
			writeD(pt.getY());
			writeD(pt.getZ());
		}

		// 2) lines
		for (Line ln : _lines) {
			writeC(2); // type = line
			writeS(ln.getName());
			int c = ln.getColor();
			int r = (c >> 16) & 0xFF;
			int g = (c >> 8) & 0xFF;
			int b = (c) & 0xFF;
			writeD(r);
			writeD(g);
			writeD(b);
			writeD(ln.isNameColored() ? 1 : 0);
			writeD(ln.getX());
			writeD(ln.getY());
			writeD(ln.getZ());
			writeD(ln.getX2());
			writeD(ln.getY2());
			writeD(ln.getZ2());
		}
	}

	// ------------------------------------------------------------------------
	// Internal classes: Point, Line

	private static class Point {
		private final String _name;
		private final int _color;
		private final boolean _isNameColored;
		private final int _x;
		private final int _y;
		private final int _z;

		public Point(String name, int color, boolean isNameColored, int x, int y, int z) {
			_name = name;
			_color = color;
			_isNameColored = isNameColored;
			_x = x;
			_y = y;
			_z = z;
		}

		public String getName() {
			return _name;
		}

		public int getColor() {
			return _color;
		}

		public boolean isNameColored() {
			return _isNameColored;
		}

		public int getX() {
			return _x;
		}

		public int getY() {
			return _y;
		}

		public int getZ() {
			return _z;
		}
	}

	private static class Line extends Point {
		private final int _x2;
		private final int _y2;
		private final int _z2;

		public Line(String name, int color, boolean isNameColored,
				int x, int y, int z,
				int x2, int y2, int z2) {
			super(name, color, isNameColored, x, y, z);
			_x2 = x2;
			_y2 = y2;
			_z2 = z2;
		}

		public int getX2() {
			return _x2;
		}

		public int getY2() {
			return _y2;
		}

		public int getZ2() {
			return _z2;
		}
	}
}
