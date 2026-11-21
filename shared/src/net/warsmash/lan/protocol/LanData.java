package net.warsmash.lan.protocol;

import java.util.ArrayList;
import java.util.List;

/** Value types shared by several messages. Encoding is canonical, so equal values always encode to equal bytes. */
public final class LanData {
	private LanData() {
	}

	public static final class SlotInfo {
		public int index;
		public int kind = LanProtocol.SLOT_OPEN;
		public int participantId = LanProtocol.NO_PARTICIPANT;
		public String name = "";
		public int computerLevel;
		public int race;
		public int team;
		public int color;
		public int handicap = 100;
		public boolean ready;
		public int pingMs;
		public int controlScheme;

		public SlotInfo copy() {
			final SlotInfo s = new SlotInfo();
			s.index = this.index;
			s.kind = this.kind;
			s.participantId = this.participantId;
			s.name = this.name;
			s.computerLevel = this.computerLevel;
			s.race = this.race;
			s.team = this.team;
			s.color = this.color;
			s.handicap = this.handicap;
			s.ready = this.ready;
			s.pingMs = this.pingMs;
			s.controlScheme = this.controlScheme;
			return s;
		}

		public void write(final ByteWriter w) throws LanProtocolException {
			w.u8(this.index).u8(this.kind).u8(this.participantId).string(this.name, LanProtocol.MAX_PLAYER_NAME)
					.u8(this.computerLevel).u8(this.race).u8(this.team).u8(this.color).u8(this.handicap)
					.bool(this.ready).u16(this.pingMs).u8(this.controlScheme);
		}

		public static SlotInfo read(final ByteReader r) throws LanProtocolException {
			final SlotInfo s = new SlotInfo();
			s.index = r.u8(LanProtocol.MAX_SLOTS - 1);
			s.kind = r.u8(LanProtocol.SLOT_COMPUTER);
			s.participantId = r.u8();
			s.name = r.name(LanProtocol.MAX_PLAYER_NAME);
			s.computerLevel = r.u8(2);
			s.race = r.u8();
			s.team = r.u8();
			s.color = r.u8();
			s.handicap = r.u8(100);
			s.ready = r.bool();
			s.pingMs = r.u16();
			s.controlScheme = r.u8(1);
			return s;
		}
	}

	/** Where a participant of the lobby can be reached, for the one that takes the lobby over when the host goes. */
	public static final class MemberInfo {
		public int participantId;
		public String address = "";
		public int listenPort;

		public MemberInfo() {
		}

		public MemberInfo(final int participantId, final String address, final int listenPort) {
			this.participantId = participantId;
			this.address = address;
			this.listenPort = listenPort;
		}

		public void write(final ByteWriter w) throws LanProtocolException {
			w.u8(this.participantId).string(this.address, 45).u16(this.listenPort);
		}

		public static MemberInfo read(final ByteReader r) throws LanProtocolException {
			return new MemberInfo(r.u8(), r.name(45), r.u16());
		}
	}

	public static final class ObserverInfo {
		public int participantId;
		public String name = "";
		public int role = LanProtocol.ROLE_OBSERVER;
		public int controlScheme;
		public int pingMs;

		public void write(final ByteWriter w) throws LanProtocolException {
			w.u8(this.participantId).string(this.name, LanProtocol.MAX_PLAYER_NAME).u8(this.role)
					.u8(this.controlScheme).u16(this.pingMs);
		}

		public static ObserverInfo read(final ByteReader r) throws LanProtocolException {
			final ObserverInfo o = new ObserverInfo();
			o.participantId = r.u8();
			o.name = r.name(LanProtocol.MAX_PLAYER_NAME);
			o.role = r.u8(2);
			o.controlScheme = r.u8(1);
			o.pingMs = r.u16();
			return o;
		}
	}

	public static final class Options {
		public String gameName = "";
		public int speed = 1;
		public int observers = LanProtocol.OBSERVERS_NONE;
		public boolean lockTeams;
		public boolean teamsTogether;
		public boolean fullSharedControl;
		public boolean randomRaces;

		public Options copy() {
			final Options o = new Options();
			o.gameName = this.gameName;
			o.speed = this.speed;
			o.observers = this.observers;
			o.lockTeams = this.lockTeams;
			o.teamsTogether = this.teamsTogether;
			o.fullSharedControl = this.fullSharedControl;
			o.randomRaces = this.randomRaces;
			return o;
		}

		public void write(final ByteWriter w) throws LanProtocolException {
			w.string(this.gameName, LanProtocol.MAX_GAME_NAME).u8(this.speed).u8(this.observers)
					.bool(this.lockTeams).bool(this.teamsTogether).bool(this.fullSharedControl).bool(this.randomRaces);
		}

		public static Options read(final ByteReader r) throws LanProtocolException {
			final Options o = new Options();
			o.gameName = r.name(LanProtocol.MAX_GAME_NAME);
			o.speed = r.u8(4);
			o.observers = r.u8(2);
			o.lockTeams = r.bool();
			o.teamsTogether = r.bool();
			o.fullSharedControl = r.bool();
			o.randomRaces = r.bool();
			return o;
		}
	}

	public static final class MapInfo {
		public static final int MAX_NAME = 64;
		public static final int MAX_SIZE = 128 * 1024 * 1024;

		public String name = "";
		/** The name the map gives itself in its settings; empty when it has none. */
		public String title = "";
		/** The playable area in cells, 0 when unknown. */
		public int playableWidth;
		public int playableHeight;
		public int size;
		/** Empty when unknown, otherwise 32 bytes. */
		public byte[] sha256 = new byte[0];
		public int crc32;

		public void write(final ByteWriter w) throws LanProtocolException {
			if (this.sha256.length != 0 && this.sha256.length != LanProtocol.SHA256_BYTES) {
				throw new LanProtocolException("bad sha256 length");
			}
			w.string(this.name, MAX_NAME).string(this.title, MAX_NAME).u16(this.playableWidth)
					.u16(this.playableHeight).i32(this.size).bytes(this.sha256).i32(this.crc32);
		}

		public static MapInfo read(final ByteReader r) throws LanProtocolException {
			final MapInfo m = new MapInfo();
			m.name = r.name(MAX_NAME);
			m.title = r.name(MAX_NAME);
			m.playableWidth = r.u16();
			m.playableHeight = r.u16();
			m.size = r.i32(0, MAX_SIZE);
			m.sha256 = r.bytes(LanProtocol.SHA256_BYTES);
			if (m.sha256.length != 0 && m.sha256.length != LanProtocol.SHA256_BYTES) {
				throw new LanProtocolException("bad sha256 length");
			}
			m.crc32 = r.i32();
			return m;
		}
	}

	public static final class ParticipantInfo {
		public int id;
		public String name = "";
		public int role;
		public int slot = LanProtocol.NO_SLOT;
		public String address = "";
		public int listenPort;
		public int controlScheme;
		/**
		 * SHA-256 of the participant's slot token: whoever takes the game over as host checks a returning player's
		 * token against it without ever having been told the token.
		 */
		public byte[] tokenHash = new byte[LanProtocol.SHA256_BYTES];

		public void write(final ByteWriter w) throws LanProtocolException {
			if (this.tokenHash.length != LanProtocol.SHA256_BYTES) {
				throw new LanProtocolException("bad token hash length");
			}
			w.u8(this.id).string(this.name, LanProtocol.MAX_PLAYER_NAME).u8(this.role).u8(this.slot)
					.string(this.address, 45).u16(this.listenPort).u8(this.controlScheme).bytes(this.tokenHash);
		}

		public static ParticipantInfo read(final ByteReader r) throws LanProtocolException {
			final ParticipantInfo p = new ParticipantInfo();
			p.id = r.u8();
			p.name = r.name(LanProtocol.MAX_PLAYER_NAME);
			p.role = r.u8(2);
			p.slot = r.u8();
			p.address = r.name(45);
			p.listenPort = r.u16();
			p.controlScheme = r.u8(1);
			p.tokenHash = r.fixed(LanProtocol.SHA256_BYTES);
			return p;
		}
	}

	public static final class CmdEntry {
		public final int slot;
		public final int seq;
		public final byte[] bytes;

		public CmdEntry(final int slot, final int seq, final byte[] bytes) {
			this.slot = slot;
			this.seq = seq;
			this.bytes = bytes;
		}

		public void write(final ByteWriter w) throws LanProtocolException {
			if (this.bytes.length > LanProtocol.MAX_COMMAND_BYTES) {
				throw new LanProtocolException("command too long");
			}
			w.u8(this.slot).i32(this.seq).bytes(this.bytes);
		}

		public static CmdEntry read(final ByteReader r) throws LanProtocolException {
			return new CmdEntry(r.u8(), r.i32(1, Integer.MAX_VALUE), r.bytes(LanProtocol.MAX_COMMAND_BYTES));
		}
	}

	public static final class PresenceEntry {
		public final int participant;
		public final int state;

		public PresenceEntry(final int participant, final int state) {
			this.participant = participant;
			this.state = state;
		}
	}

	public static final class LagEntry {
		public final int participant;
		public final int reason;
		public final int waitedMs;

		public LagEntry(final int participant, final int reason, final int waitedMs) {
			this.participant = participant;
			this.reason = reason;
			this.waitedMs = waitedMs;
		}
	}

	public static final class ChecksumGroup {
		public final int crc;
		public final int[] participants;

		public ChecksumGroup(final int crc, final int[] participants) {
			this.crc = crc;
			this.participants = participants;
		}
	}

	/** One turn: the commands and presence changes that every participant applies before the same simulation steps. */
	public static final class TurnData {
		public final int n;
		public final int steps;
		public final List<CmdEntry> cmds;
		public final List<PresenceEntry> presence;

		public TurnData(final int n, final int steps, final List<CmdEntry> cmds, final List<PresenceEntry> presence) {
			this.n = n;
			this.steps = steps;
			this.cmds = cmds;
			this.presence = presence;
		}

		public void write(final ByteWriter w) throws LanProtocolException {
			w.i32(this.n).u8(this.steps).u16(this.cmds.size());
			for (final CmdEntry c : this.cmds) {
				c.write(w);
			}
			w.u16(this.presence.size());
			for (final PresenceEntry p : this.presence) {
				w.u8(p.participant).u8(p.state);
			}
		}

		public static TurnData read(final ByteReader r) throws LanProtocolException {
			final int n = r.i32(0, Integer.MAX_VALUE);
			final int steps = r.u8();
			int count = r.count(LanProtocol.MAX_COMMANDS_PER_TURN * LanProtocol.MAX_PARTICIPANTS, 7);
			final List<CmdEntry> cmds = new ArrayList<CmdEntry>(Math.min(count, 64));
			for (int i = 0; i < count; i++) {
				cmds.add(CmdEntry.read(r));
			}
			count = r.count(LanProtocol.MAX_PARTICIPANTS * 4, 2);
			final List<PresenceEntry> presence = new ArrayList<PresenceEntry>(Math.min(count, 16));
			for (int i = 0; i < count; i++) {
				presence.add(new PresenceEntry(r.u8(), r.u8(3)));
			}
			return new TurnData(n, steps, cmds, presence);
		}

		public byte[] toBytes() throws LanProtocolException {
			final ByteWriter w = new ByteWriter(LanProtocol.MAX_FRAME_BODY);
			write(w);
			return w.toByteArray();
		}

		public static TurnData fromBytes(final byte[] b) throws LanProtocolException {
			final ByteReader r = new ByteReader(b);
			final TurnData t = read(r);
			r.expectEnd();
			return t;
		}
	}

	/** Advertisement carried by UDP discovery datagrams. */
	public static final class GameInfo {
		public long gameId;
		public int protocolVersion = LanProtocol.PROTOCOL_VERSION;
		public String engineVersion = "";
		public String buildCommit = "";
		public String hostName = "";
		public String gameName = "";
		public String mapName = "";
		/** The map's own name; empty when unknown. */
		public String mapTitle = "";
		public int mapSize;
		public int mapWidth;
		public int mapHeight;
		/** Empty when unknown, otherwise 32 bytes: what a search compares with the maps this device has. */
		public byte[] mapSha256 = new byte[0];
		public int mapCrc32;
		public int observers;
		public boolean lockTeams;
		public boolean teamsTogether;
		public boolean fullSharedControl;
		public boolean randomRaces;
		public int players;
		public int maxPlayers;
		public int state;
		public int hostId;
		public int tcpPort;
		public int speed;
		/** The host's profile portrait as it names it; not part of the announcement itself (see GamePortrait), empty until learnt. */
		public String portrait = "";

		public GameInfo copy() {
			final GameInfo g = new GameInfo();
			g.gameId = this.gameId;
			g.protocolVersion = this.protocolVersion;
			g.engineVersion = this.engineVersion;
			g.buildCommit = this.buildCommit;
			g.hostName = this.hostName;
			g.gameName = this.gameName;
			g.mapName = this.mapName;
			g.mapTitle = this.mapTitle;
			g.mapSize = this.mapSize;
			g.mapWidth = this.mapWidth;
			g.mapHeight = this.mapHeight;
			g.mapCrc32 = this.mapCrc32;
			g.observers = this.observers;
			g.lockTeams = this.lockTeams;
			g.teamsTogether = this.teamsTogether;
			g.fullSharedControl = this.fullSharedControl;
			g.randomRaces = this.randomRaces;
			g.players = this.players;
			g.maxPlayers = this.maxPlayers;
			g.state = this.state;
			g.hostId = this.hostId;
			g.tcpPort = this.tcpPort;
			g.speed = this.speed;
			g.portrait = this.portrait;
			g.mapSha256 = this.mapSha256.clone();
			return g;
		}

		public void write(final ByteWriter w) throws LanProtocolException {
			w.i64(this.gameId).u16(this.protocolVersion).string(this.engineVersion, 32).string(this.buildCommit, 48)
					.string(this.hostName, LanProtocol.MAX_PLAYER_NAME).string(this.gameName, LanProtocol.MAX_GAME_NAME)
					.string(this.mapName, MapInfo.MAX_NAME).string(this.mapTitle, MapInfo.MAX_NAME)
					.i32(this.mapSize).u16(this.mapWidth).u16(this.mapHeight)
					.bytes(this.mapSha256).i32(this.mapCrc32).u8(this.observers).bool(this.lockTeams)
					.bool(this.teamsTogether).bool(this.fullSharedControl).bool(this.randomRaces).u8(this.players)
					.u8(this.maxPlayers).u8(this.state)
					.u8(this.hostId).u16(this.tcpPort).u8(this.speed);
		}

		public static GameInfo read(final ByteReader r) throws LanProtocolException {
			final GameInfo g = new GameInfo();
			g.gameId = r.i64();
			g.protocolVersion = r.u16();
			g.engineVersion = r.name(32);
			g.buildCommit = r.name(48);
			g.hostName = r.name(LanProtocol.MAX_PLAYER_NAME);
			g.gameName = r.name(LanProtocol.MAX_GAME_NAME);
			g.mapName = r.name(MapInfo.MAX_NAME);
			g.mapTitle = r.name(MapInfo.MAX_NAME);
			g.mapSize = r.i32(0, MapInfo.MAX_SIZE);
			g.mapWidth = r.u16();
			g.mapHeight = r.u16();
			g.mapSha256 = r.bytes(LanProtocol.SHA256_BYTES);
			if (g.mapSha256.length != 0 && g.mapSha256.length != LanProtocol.SHA256_BYTES) {
				throw new LanProtocolException("bad sha256 length");
			}
			g.mapCrc32 = r.i32();
			g.observers = r.u8(2);
			g.lockTeams = r.bool();
			g.teamsTogether = r.bool();
			g.fullSharedControl = r.bool();
			g.randomRaces = r.bool();
			g.players = r.u8(LanProtocol.MAX_SLOTS);
			g.maxPlayers = r.u8(LanProtocol.MAX_SLOTS);
			g.state = r.u8(3);
			g.hostId = r.u8();
			g.tcpPort = r.u16();
			g.speed = r.u8(4);
			return g;
		}
	}
}
