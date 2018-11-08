package me.libraryaddict.disguise.utilities.parser;

import me.libraryaddict.disguise.DisguiseConfig;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Monster;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.*;

/**
 * Created by libraryaddict on 14/10/2018.
 */
public class DisguisePermissions {
    private class PermissionStorage {
        private DisguisePerm disguisePerm;
        private List<String> permittedOptions = new ArrayList<>();
        private List<String> negatedOptions = new ArrayList<>();
        private boolean wildcardAllow = false;

        public PermissionStorage(DisguisePerm disguisePerm) {
            this.disguisePerm = disguisePerm;
        }

        public DisguisePerm getDisguise() {
            return this.disguisePerm;
        }
    }

    private class ParsedPermission {
        private Vector<DisguisePerm> disguisePerm;
        private HashMap<String, Boolean> options;
        private boolean negated;
        /**
         * 0 = Names a specific disguise
         * 1 = Animal
         * 2 = Monster
         * 3... = etc
         * 4 = * = Disguise wildcard
         */
        private byte inheritance;
        private boolean wildcardCommand;

        public ParsedPermission(DisguisePerm[] disguisePerm, HashMap<String, Boolean> options, byte inheritance,
                boolean wildcardCommand) {
            this.disguisePerm = new Vector<>(Arrays.asList(disguisePerm));
            this.options = options;
            this.inheritance = inheritance;
            this.wildcardCommand = wildcardCommand;
        }

        public boolean isWildcardCommand() {
            return wildcardCommand;
        }

        public boolean isDisguise(DisguisePerm perm) {
            return disguisePerm.contains(perm);
        }

        public boolean isNegated() {
            return negated;
        }

        public void setNegated(boolean negated) {
            this.negated = negated;
        }

        public byte getInheritance() {
            return inheritance;
        }
    }

    class DisguisePermitted {
        private boolean strictAllowed;
        private List<String> optionsAllowed;
        private List<String> optionsForbidden;

        public DisguisePermitted(List<String> optionsAllowed, List<String> optionsForbidden, boolean strict) {
            this.strictAllowed = strict;
            this.optionsAllowed = optionsAllowed;
            this.optionsForbidden = optionsForbidden;
        }

        public boolean isStrictAllowed() {
            return strictAllowed;
        }

        public List<String> getOptionsAllowed() {
            return optionsAllowed;
        }

        public List<String> getOptionsForbidden() {
            return optionsForbidden;
        }
    }

    /**
     * List of PermissionStorage that the permission holder is able to use
     */
    private List<PermissionStorage> disguises = new ArrayList<>();

    /**
     * @param permissionHolder The permissions to check
     * @param commandName      A lowercase string consisting of the name of one of Lib's Disguises commands
     */
    public DisguisePermissions(Permissible permissionHolder, String commandName) {
        loadPermissions(permissionHolder, commandName.toLowerCase());
    }

    /**
     * @return If any of the disguises can be used
     */
    public boolean hasPermissions() {
        return !disguises.isEmpty();
    }

    public Collection<DisguisePerm> getAllowed() {
        ArrayList<DisguisePerm> list = new ArrayList<>();

        for (PermissionStorage allowed : disguises) {
            if (list.contains(allowed.getDisguise())) {
                continue;
            }

            list.add(allowed.getDisguise());
        }

        list.sort((perm1, perm2) -> String.CASE_INSENSITIVE_ORDER.compare(perm1.toReadable(), perm2.toReadable()));

        return list;
    }

    private ParsedPermission parsePermission(String permission) {
        // libsdisguises.disguise.sheep
        String[] split = permission.split("\\.");

        String disguiseName = split[2];

        DisguisePerm dPerm = DisguiseParser.getDisguisePerm(disguiseName);
        HashMap<String, Boolean> options = getOptions(permission);

        // If this refers to a specific disguise
        if (dPerm != null) {
            return new ParsedPermission(new DisguisePerm[]{dPerm}, options, (byte) 0, split[1].equals("*"));
        }

        // If the disguise can't be found, it may be refering to a range
        List<DisguisePerm> disguisePerms = new ArrayList<>();
        int inheritance = 0;

        for (DisguisePerm disguisePerm : DisguiseParser.getDisguisePerms()) {
            int inherit = getInheritance(disguisePerm, disguiseName);

            if (inherit < 0) {
                continue;
            }

            inheritance = inherit;

            disguisePerms.add(disguisePerm);
        }

        // If there were no disguises that can be found by that name
        if (disguisePerms.isEmpty()) {
            return null;
        }

        return new ParsedPermission(disguisePerms.toArray(new DisguisePerm[0]), options, (byte) inheritance,
                split[1].equals("*"));
    }

    /**
     * Calculate permissions.
     * <p>
     * A specified disguise (cow) and disguise range (animal) differs in that
     * A disguise range cannot negate a specific disguise, players will be allowed to use cow if animal is negated
     * <p>
     * Options on a permission limits the player, if the options start with a - then only those options can't be used
     * on a disguise
     * If they're using multiple permissions targetting the same disguise, it attempts to check for a permission that
     * can be used with the provided requirements
     * If a permission is negated, then unless specifically permitted, those permissions can't be used. It obeys the
     * laws of ranges and specific disguises
     */
    private void loadPermissions(Permissible sender, String commandName) {
        String permissionNode = "libsdisguises." + commandName + ".";
        Map<String, Boolean> permissions = new HashMap<>();

        // If the command sender is OP, then this will work even as the below code doesn't
        // libsdisguises.[command].[disguise].[options]
        // They can use all commands, all disguises, all options
        if (sender.hasPermission("libsdisguises.*.*.*")) {
            permissions.put("libsdisguises.*.*.*", true);
        }

        for (PermissionAttachmentInfo permission : sender.getEffectivePermissions()) {
            String perm = permission.getPermission().toLowerCase();

            String[] split = perm.split("\\.");

            // If there are not enough arguments
            if (split.length < 3) {
                continue;
            }

            // If this is not a lib's disguises permission
            if (!split[0].equals("libsdisguises")) {
                continue;
            }

            // If the command name does not match
            if (!split[1].equals("*") && !split[1].equals(commandName)) {
                continue;
            }

            // If it's already contained in the map, and is true. Allow negating permissions to continue
            if (permissions.containsKey(perm) && permission.getValue()) {
                continue;
            }

            permissions.put(perm, permission.getValue());
        }

        // First get all the disguises that can be affected
        // Then load all the permissions we can
        // Each time there's a parent permission set, the child inherits unless specified in a child of that parent

        // DisguisePerm[]
        // Option[]
        // Negated

        List<ParsedPermission> list = new ArrayList<>();

        for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
            ParsedPermission temp = parsePermission(entry.getKey());

            if (temp == null) {
                continue;
            }

            temp.setNegated(!entry.getValue());

            list.add(temp);
        }

        // Sorted from 5 to 0 where "*" is first and "Cow" is last
        // Negated permissions are last in each inheritance, so false, false, true, true

        list.sort((t1, t2) -> {
            // Wilcard commands have little say, so they go first so they can be negated by following permissions
            if (t1.isWildcardCommand() != t2.isWildcardCommand()) {
                return Boolean.compare(t2.isWildcardCommand(), t1.isWildcardCommand());
            }

            if (t1.getInheritance() == t2.getInheritance()) {
                return Boolean.compare(t1.isNegated(), t2.isNegated());
            }

            return t2.getInheritance() - t1.getInheritance();
        });

        for (DisguisePerm disguisePerm : DisguiseParser.getDisguisePerms()) {
            // Use boolean instead of setting to null, to inherit
            boolean disabled = true;
            PermissionStorage storage = new PermissionStorage(disguisePerm);

            for (ParsedPermission parsedPermission : list) {
                // If this parsed permission doesn't handle this disguise type
                if (!parsedPermission.isDisguise(disguisePerm)) {
                    continue;
                }

                // A negated permission with no options, disables the disguise
                if (parsedPermission.isNegated() && parsedPermission.options.isEmpty()) {
                    // Remove disguise
                    disabled = true;
                    continue;
                }

                // The permission is negated, and only has negated options. Should mean something, but to most people
                // it's nonsense and should be ignored.
                if (parsedPermission.isNegated() && !parsedPermission.options.values().contains(true)) {
                    continue;
                }

                if (!parsedPermission.isNegated()) {
                    // Enable the disguise if permission isn't negated
                    // A negated permission cannot enable access
                    if (disabled) {
                        disabled = false;
                    }

                    // If the child disguise does not have any options defined, give them wildcard by default if
                    // config allows
                    if (parsedPermission.options.isEmpty() && !DisguiseConfig.isExplictDisguisePermissions()) {
                        storage.wildcardAllow = true;
                        // If this disguise has options defined, unless wildcard was explictly given then remove it
                    } else if (!storage.permittedOptions.contains("*")) {
                        storage.wildcardAllow = false;
                    }
                }

                for (Map.Entry<String, Boolean> entry : parsedPermission.options.entrySet()) {
                    // If permission is negated, reverse the option from 'allowed' to 'denied' or vice versa
                    boolean allowUse = parsedPermission.isNegated() ? !entry.getValue() : entry.getValue();

                    storage.permittedOptions.remove(entry.getKey());
                    storage.negatedOptions.remove(entry.getKey());

                    // Handle wildcard options
                    if (entry.getKey().equals("*")) {
                        // If it's a negated wildcard, then they don't want the user to use anything
                        // If it's a permitted wildcard, then they want the user to use everything

                        // Both want to clear the existing restrictions
                        storage.permittedOptions.clear();
                        storage.negatedOptions.clear();

                        // Add wildcard allow so if the user later defines "setBaby" just to be sure, it doesn't
                        // limit them to setbaby
                        storage.wildcardAllow = allowUse;

                        // Negated wants to prevent the use of all options
                        if (!allowUse) {
                            storage.permittedOptions.add("nooptions");
                        }
                    }

                    if (allowUse) {
                        storage.permittedOptions.add(entry.getKey());
                    } else {
                        storage.negatedOptions.add(entry.getKey());
                    }
                }
            }

            // Disguise is not allowed, continue
            if (disabled) {
                continue;
            }

            // If invisibility was disabled in the config, ignore permissions and make sure it's disabled
            if (DisguiseConfig.isDisabledInvisibility()) {
                storage.permittedOptions.remove("setinvisible");
                storage.negatedOptions.add("setinvisible");
            }

            disguises.add(storage);
        }
    }

    private int getInheritance(DisguisePerm disguisePerm, String permissionName) {
        DisguiseType disguiseType = disguisePerm.getType();

        if (permissionName.equals("ageable")) {
            if (Ageable.class.isAssignableFrom(disguiseType.getEntityClass())) {
                return 1;
            }
        } else if (permissionName.equals("monster") || permissionName.equals("monsters")) {
            if (Monster.class.isAssignableFrom(disguiseType.getEntityClass())) {
                return 2;
            }
        } else if (permissionName.equals("animal") || permissionName.equals("animals")) {
            if (Animals.class.isAssignableFrom(disguiseType.getEntityClass())) {
                return 2;
            }
        } else if (permissionName.equals("mob")) {
            if (disguiseType.isMob()) {
                return 3;
            }
        } else if (permissionName.equals("misc")) {
            if (disguiseType.isMisc()) {
                return 3;
            }
        } else if (permissionName.equals("*")) {
            return 4;
        }

        return -1;
    }

    private HashMap<String, Boolean> getOptions(String perm) {
        HashMap<String, Boolean> options = new HashMap<>();
        String[] split = perm.split("\\.");

        for (int i = 3; i < split.length; i++) {
            String option = split[i];
            boolean negated = option.startsWith("-");

            if (negated) {
                option = option.substring(1);
            }

            if (option.equals("baby")) {
                option = "setbaby";
            }

            options.put(option, !negated);
        }

        return options;
    }

    /**
     * If this DisguisePermission can use the provided disguise and options
     *
     * @param disguisePerm
     * @param disguiseOptions
     * @return true if permitted
     */
    public boolean isAllowedDisguise(DisguisePerm disguisePerm, Collection<String> disguiseOptions) {
        PermissionStorage storage = getStorage(disguisePerm);

        if (storage == null) {
            return false;
        }

        // If the disguise doesn't have a wildcard allow on it
        // If the user is limited to a select range of options, and not all the options were found in the allowed
        // options
        if (!storage.wildcardAllow && !storage.permittedOptions.isEmpty() &&
                !disguiseOptions.stream().allMatch(option -> storage.permittedOptions.contains(option.toLowerCase()))) {
            return false;
        }

        // If the user is using a forbidden option, return false. Otherwise true
        return disguiseOptions.stream().noneMatch(option -> storage.negatedOptions.contains(option.toLowerCase()));
    }

    public boolean isAllowedDisguise(DisguisePerm disguisePerm) {
        return getStorage(disguisePerm) != null;
    }

    private PermissionStorage getStorage(DisguisePerm disguisePerm) {
        return disguises.stream().filter(disguise -> disguise.getDisguise().equals(disguisePerm)).findAny()
                .orElse(null);
    }
}