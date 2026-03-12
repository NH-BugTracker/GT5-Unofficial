package gregtech.gtnhteams;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

public class CashTeamData implements ITeamData {

    private long cash;

    @Override
    public void writeToNBT(NBTTagCompound NBT) {
        NBT.setLong("cash", cash);
    }

    @Override
    public void readFromNBT(NBTTagCompound NBT) {
        cash = NBT.getLong("cash");
    }

    @Override
    public void mergeData(ITeamData data) {
        if (data instanceof CashTeamData cashTeam) {
            cash += cashTeam.cash;
        }
    }

    public static CashTeamData getCashTeamFromPlayer(EntityPlayer player) {
        Team team = TeamManager.getTeamByPlayer(player.getUniqueID());
        if (team == null) return null;

        return (CashTeamData) team.getData("cashteam");
    }

    public long getCash() {
        return cash;
    }

    public void addCash(long amount) {
        cash += amount;
        TeamWorldSavedData.markForSaving();
    }

    public boolean spendCash(long amount) {
        if (amount > cash) return false;
        cash -= amount;
        TeamWorldSavedData.markForSaving();
        return true;
    }
}
