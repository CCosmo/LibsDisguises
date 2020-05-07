package me.libraryaddict.disguise.disguisetypes;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerInfoAction;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import lombok.Getter;
import lombok.Setter;
import me.libraryaddict.disguise.DisguiseConfig;
import me.libraryaddict.disguise.LibsDisguises;
import me.libraryaddict.disguise.disguisetypes.watchers.PlayerWatcher;
import me.libraryaddict.disguise.utilities.DisguiseUtilities;
import me.libraryaddict.disguise.utilities.reflection.LibsProfileLookup;
import me.libraryaddict.disguise.utilities.reflection.ReflectionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

public class PlayerDisguise extends TargetedDisguise {
    private transient LibsProfileLookup currentLookup;
    private WrappedGameProfile gameProfile;
    private String playerName = "Herobrine";
    private String skinToUse;
    private boolean nameVisible = true;
    /**
     * Has someone set name visible explicitly?
     */
    private boolean explicitNameVisible = false;
    private UUID uuid = UUID.randomUUID();
    @Getter
    @Setter
    private boolean dynamicName;
    private volatile DisguiseUtilities.DScoreTeam scoreboardName;

    private PlayerDisguise() {
        super(DisguiseType.PLAYER);
    }

    public PlayerDisguise(Player player) {
        this(ReflectionManager.getGameProfile(player));
    }

    public PlayerDisguise(Player player, Player skinToUse) {
        this(ReflectionManager.getGameProfile(player), ReflectionManager.getGameProfile(skinToUse));
    }

    public PlayerDisguise(String name) {
        this(name, name);
    }

    public PlayerDisguise(String name, String skinToUse) {
        this();

        setName(name);
        setSkin(skinToUse);

        createDisguise();
    }

    public PlayerDisguise(WrappedGameProfile gameProfile) {
        this();

        setName(gameProfile.getName());

        this.gameProfile = ReflectionManager.getGameProfileWithThisSkin(uuid, gameProfile.getName(), gameProfile);

        createDisguise();
    }

    public PlayerDisguise(WrappedGameProfile gameProfile, WrappedGameProfile skinToUse) {
        this();

        setName(gameProfile.getName());

        this.gameProfile = ReflectionManager.getGameProfile(uuid, gameProfile.getName());

        setSkin(skinToUse);

        createDisguise();
    }

    @Deprecated
    public DisguiseUtilities.DScoreTeam getScoreboardName() {
        if (!DisguiseConfig.isScoreboardNames()) {
            throw new IllegalStateException("Cannot use this method when it's been disabled in config!");
        }

        if (scoreboardName == null) {
            scoreboardName = DisguiseUtilities.createExtendedName(getName());
        }

        return scoreboardName;
    }

    private void setScoreboardName(String[] split) {
        getScoreboardName().setSplit(split);
    }

    private boolean isStaticName(String name) {
        return name != null && (name.equalsIgnoreCase("Dinnerbone") || name.equalsIgnoreCase("Grumm"));
    }

    public boolean hasScoreboardName() {
        if (!DisguiseConfig.isArmorstandsName() && isStaticName(getName())) {
            return false;
        }

        return DisguiseConfig.isScoreboardNames();
    }

    public String getProfileName() {
        return hasScoreboardName() ? getScoreboardName().getPlayer() : getName();
    }

    public UUID getUUID() {
        return uuid;
    }

    public boolean isNameVisible() {
        return nameVisible;
    }

    public PlayerDisguise setNameVisible(boolean nameVisible) {
        return setNameVisible(nameVisible, false);
    }

    private PlayerDisguise setNameVisible(boolean nameVisible, boolean setInternally) {
        if (isNameVisible() == nameVisible || (setInternally && explicitNameVisible)) {
            return this;
        }

        if (!setInternally) {
            explicitNameVisible = true;
        }

        if (isDisguiseInUse()) {
            if (DisguiseConfig.isArmorstandsName()) {
                this.nameVisible = nameVisible;
                sendArmorStands(isNameVisible() ? getMultiName() : new String[0]);
            } else if (!DisguiseConfig.isScoreboardNames()) {
                if (stopDisguise()) {
                    this.nameVisible = nameVisible;

                    if (!startDisguise()) {
                        throw new IllegalStateException("Unable to restart disguise");
                    }
                } else {
                    throw new IllegalStateException("Unable to restart disguise");
                }
            } else {
                this.nameVisible = nameVisible;
                DisguiseUtilities.updateExtendedName(this);
            }
        } else {
            this.nameVisible = nameVisible;
        }

        return this;
    }

    @Override
    public PlayerDisguise addPlayer(Player player) {
        return (PlayerDisguise) super.addPlayer(player);
    }

    @Override
    public PlayerDisguise addPlayer(String playername) {
        return (PlayerDisguise) super.addPlayer(playername);
    }

    @Override
    public PlayerDisguise clone() {
        PlayerDisguise disguise = new PlayerDisguise();

        if (currentLookup == null && gameProfile != null) {
            disguise.skinToUse = getSkin();
            disguise.gameProfile = ReflectionManager
                    .getGameProfileWithThisSkin(disguise.uuid, getGameProfile().getName(), getGameProfile());
        } else {
            disguise.setSkin(getSkin());
        }

        disguise.setName(getName());
        disguise.nameVisible = isNameVisible();
        disguise.explicitNameVisible = explicitNameVisible;
        disguise.setDynamicName(isDynamicName());

        clone(disguise);

        return disguise;
    }

    public WrappedGameProfile getGameProfile() {
        if (gameProfile == null) {
            if (getSkin() != null) {
                gameProfile = ReflectionManager.getGameProfile(uuid, getProfileName());
            } else {
                gameProfile = ReflectionManager.getGameProfileWithThisSkin(uuid, getProfileName(),
                        DisguiseUtilities.getProfileFromMojang(this));
            }
        }

        return gameProfile;
    }

    public void setGameProfile(WrappedGameProfile gameProfile) {
        this.gameProfile = ReflectionManager.getGameProfileWithThisSkin(uuid, gameProfile.getName(), gameProfile);
    }

    public String getName() {
        return playerName;
    }

    public void setName(String name) {
        if (getName().equals("<Inherit>") && getEntity() != null) {
            name = getEntity().getCustomName();

            if (name == null || name.isEmpty()) {
                name = getEntity().getType().name();
            }
        }

        if (name.equals(playerName)) {
            return;
        }

        int cLimit;

        switch (DisguiseConfig.getPlayerNameType()) {
            case TEAMS:
                cLimit = 16 * 2;
                break;
            case EXTENDED:
                cLimit = 16 * 3;
                break;
            case ARMORSTANDS:
                cLimit = 256;
                break;
            default:
                cLimit = 16;
                break;
        }

        if (name.length() > cLimit) {
            name = name.substring(0, cLimit);
        }

        if (isDisguiseInUse()) {
            if (DisguiseConfig.isArmorstandsName()) {
                playerName = name;

                setNameVisible(!name.isEmpty(), true);
                setMultiName(DisguiseUtilities.splitNewLine(name));
            } else {
                boolean resendDisguise = false;

                if (DisguiseConfig.isScoreboardNames() && !isStaticName(name)) {
                    DisguiseUtilities.DScoreTeam team = getScoreboardName();
                    String[] split = DisguiseUtilities.getExtendedNameSplit(team.getPlayer(), name);

                    resendDisguise = !split[1].equals(team.getPlayer());
                    setScoreboardName(split);
                }

                resendDisguise = !DisguiseConfig.isScoreboardNames() || isStaticName(name) || isStaticName(getName()) ||
                        resendDisguise;

                if (resendDisguise) {
                    if (stopDisguise()) {
                        if (getName().isEmpty() && !name.isEmpty()) {
                            setNameVisible(true, true);
                        } else if (!getName().isEmpty() && name.isEmpty()) {
                            setNameVisible(false, true);
                        }

                        playerName = name;

                        if (gameProfile != null) {
                            gameProfile = ReflectionManager
                                    .getGameProfileWithThisSkin(uuid, getProfileName(), getGameProfile());
                        }

                        if (!startDisguise()) {
                            throw new IllegalStateException("Unable to restart disguise");
                        }
                    } else {
                        throw new IllegalStateException("Unable to restart disguise");
                    }
                } else {
                    if (getName().isEmpty() && !name.isEmpty() && !isNameVisible()) {
                        setNameVisible(true, true);
                    } else if (!getName().isEmpty() && name.isEmpty() && isNameVisible()) {
                        setNameVisible(false, true);
                    } else {
                        DisguiseUtilities.updateExtendedName(this);
                    }

                    playerName = name;
                }
            }

            if (isDisplayedInTab()) {
                PacketContainer addTab = DisguiseUtilities.getTabPacket(this, PlayerInfoAction.UPDATE_DISPLAY_NAME);

                try {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (!canSee(player))
                            continue;

                        ProtocolLibrary.getProtocolManager().sendServerPacket(player, addTab);
                    }
                }
                catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        } else {
            if (scoreboardName != null) {
                DisguiseUtilities.DScoreTeam team = getScoreboardName();
                String[] split = DisguiseUtilities.getExtendedNameSplit(team.getPlayer(), name);

                setScoreboardName(split);
            }

            if (DisguiseConfig.isScoreboardNames()) {
                setMultiName(DisguiseUtilities.splitNewLine(name));
            }

            setNameVisible(!name.isEmpty(), true);
            playerName = name;

            if (gameProfile != null) {
                gameProfile = ReflectionManager.getGameProfileWithThisSkin(uuid, getProfileName(), getGameProfile());
            }
        }
    }

    public String getSkin() {
        return skinToUse;
    }

    public PlayerDisguise setSkin(String newSkin) {
        if (newSkin != null && newSkin.length() > 70 && newSkin.startsWith("{") && newSkin.endsWith("}")) {
            try {
                return setSkin(DisguiseUtilities.getGson().fromJson(newSkin, WrappedGameProfile.class));
            }
            catch (Exception ex) {
                if (!"12345".equals("%%__USER__%%")) {
                    throw new IllegalArgumentException(String.format(
                            "The skin %s is too long to normally be a playername, but cannot be parsed to a " +
                                    "GameProfile!", newSkin));
                }
            }
        }

        if (newSkin != null && newSkin.length() > 16) {
            newSkin = null;
        }

        String oldSkin = skinToUse;
        skinToUse = newSkin;

        if (newSkin == null) {
            currentLookup = null;
            gameProfile = null;
        } else {
            if (newSkin.length() > 16) {
                skinToUse = newSkin.substring(0, 16);
            }

            if (newSkin.equals(oldSkin)) {
                return this;
            }

            if (isDisguiseInUse()) {
                currentLookup = new LibsProfileLookup() {
                    @Override
                    public void onLookup(WrappedGameProfile gameProfile) {
                        if (currentLookup != this || gameProfile == null || gameProfile.getProperties().isEmpty())
                            return;

                        setSkin(gameProfile);

                        currentLookup = null;
                    }
                };

                WrappedGameProfile gameProfile = DisguiseUtilities.getProfileFromMojang(this.skinToUse, currentLookup,
                        LibsDisguises.getInstance().getConfig().getBoolean("ContactMojangServers", true));

                if (gameProfile != null) {
                    setSkin(gameProfile);
                }
            }
        }

        return this;
    }

    /**
     * Set the GameProfile, without tampering.
     *
     * @param gameProfile GameProfile
     * @return
     */
    public PlayerDisguise setSkin(WrappedGameProfile gameProfile) {
        if (gameProfile == null) {
            this.gameProfile = null;
            this.skinToUse = null;
            return this;
        }

        currentLookup = null;

        this.skinToUse = gameProfile.getName();
        this.gameProfile = ReflectionManager.getGameProfileWithThisSkin(uuid, getProfileName(), gameProfile);

        refreshDisguise();

        return this;
    }

    private void refreshDisguise() {
        if (DisguiseUtilities.isDisguiseInUse(this)) {
            if (isDisplayedInTab()) {
                PacketContainer addTab = DisguiseUtilities.getTabPacket(this, PlayerInfoAction.ADD_PLAYER);

                PacketContainer deleteTab = addTab.shallowClone();
                deleteTab.getPlayerInfoAction().write(0, PlayerInfoAction.REMOVE_PLAYER);

                try {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (!canSee(player))
                            continue;

                        ProtocolLibrary.getProtocolManager().sendServerPacket(player, deleteTab);
                        ProtocolLibrary.getProtocolManager().sendServerPacket(player, addTab);
                    }
                }
                catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }

            DisguiseUtilities.refreshTrackers(this);
        }
    }

    @Override
    public PlayerWatcher getWatcher() {
        return (PlayerWatcher) super.getWatcher();
    }

    @Override
    public PlayerDisguise setWatcher(FlagWatcher newWatcher) {
        return (PlayerDisguise) super.setWatcher(newWatcher);
    }

    public boolean isDisplayedInTab() {
        return getWatcher().isDisplayedInTab();
    }

    public void setDisplayedInTab(boolean showPlayerInTab) {
        getWatcher().setDisplayedInTab(showPlayerInTab);
    }

    @Override
    public boolean isPlayerDisguise() {
        return true;
    }

    @Override
    public PlayerDisguise removePlayer(Player player) {
        return (PlayerDisguise) super.removePlayer(player);
    }

    @Override
    public PlayerDisguise removePlayer(String playername) {
        return (PlayerDisguise) super.removePlayer(playername);
    }

    @Override
    public PlayerDisguise setDisguiseTarget(TargetType newTargetType) {
        return (PlayerDisguise) super.setDisguiseTarget(newTargetType);
    }

    @Override
    public PlayerDisguise setEntity(Entity entity) {
        return (PlayerDisguise) super.setEntity(entity);
    }

    @Override
    public PlayerDisguise setHearSelfDisguise(boolean hearSelfDisguise) {
        return (PlayerDisguise) super.setHearSelfDisguise(hearSelfDisguise);
    }

    @Override
    public PlayerDisguise setHideArmorFromSelf(boolean hideArmor) {
        return (PlayerDisguise) super.setHideArmorFromSelf(hideArmor);
    }

    @Override
    public PlayerDisguise setHideHeldItemFromSelf(boolean hideHeldItem) {
        return (PlayerDisguise) super.setHideHeldItemFromSelf(hideHeldItem);
    }

    @Override
    public PlayerDisguise setKeepDisguiseOnPlayerDeath(boolean keepDisguise) {
        return (PlayerDisguise) super.setKeepDisguiseOnPlayerDeath(keepDisguise);
    }

    @Override
    public PlayerDisguise setModifyBoundingBox(boolean modifyBox) {
        return (PlayerDisguise) super.setModifyBoundingBox(modifyBox);
    }

    @Override
    public PlayerDisguise setReplaceSounds(boolean areSoundsReplaced) {
        return (PlayerDisguise) super.setReplaceSounds(areSoundsReplaced);
    }

    @Override
    public boolean startDisguise() {
        if (isDisguiseInUse()) {
            return false;
        }

        if (skinToUse != null && gameProfile == null) {
            currentLookup = new LibsProfileLookup() {
                @Override
                public void onLookup(WrappedGameProfile gameProfile) {
                    if (currentLookup != this || gameProfile == null || gameProfile.getProperties().isEmpty())
                        return;

                    setSkin(gameProfile);

                    currentLookup = null;
                }
            };

            WrappedGameProfile gameProfile = DisguiseUtilities.getProfileFromMojang(this.skinToUse, currentLookup,
                    LibsDisguises.getInstance().getConfig().getBoolean("ContactMojangServers", true));

            if (gameProfile != null) {
                setSkin(gameProfile);
            }
        }

        if (isDynamicName()) {
            String name = getEntity().getCustomName();

            if (name == null) {
                name = "";
            }

            if (!getName().equals(name)) {
                setName(name);
            }
        } else if (getName().equals("<Inherit>") && getEntity() != null) {
            String name = getEntity().getCustomName();

            if (name == null || name.isEmpty()) {
                name = getEntity().getType().name();
            }

            setName(name);
        }

        boolean result = super.startDisguise();

        if (result && hasScoreboardName()) {
            DisguiseUtilities.registerExtendedName(this);
        }

        return result;
    }

    @Override
    public PlayerDisguise setVelocitySent(boolean sendVelocity) {
        return (PlayerDisguise) super.setVelocitySent(sendVelocity);
    }

    @Override
    public PlayerDisguise setViewSelfDisguise(boolean viewSelfDisguise) {
        return (PlayerDisguise) super.setViewSelfDisguise(viewSelfDisguise);
    }

    @Override
    public PlayerDisguise silentlyAddPlayer(String playername) {
        return (PlayerDisguise) super.silentlyAddPlayer(playername);
    }

    @Override
    public PlayerDisguise silentlyRemovePlayer(String playername) {
        return (PlayerDisguise) super.silentlyRemovePlayer(playername);
    }

    @Override
    public boolean removeDisguise(boolean disguiseBeingReplaced) {
        boolean result = super.removeDisguise(disguiseBeingReplaced);

        if (result && hasScoreboardName()) {
            if (disguiseBeingReplaced) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        DisguiseUtilities.unregisterExtendedName(PlayerDisguise.this);
                    }
                }.runTaskLater(LibsDisguises.getInstance(), 5);
            } else {
                DisguiseUtilities.unregisterExtendedName(this);
            }
        }

        return result;
    }
}
