package betterquesting.client.gui2.editors;

import betterquesting.abs.misc.GuiAnchor;
import betterquesting.api.client.gui.misc.INeedsRefresh;
import betterquesting.api.client.gui.misc.IVolatileScreen;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.api2.client.gui.controls.IPanelButton;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.controls.PanelButtonStorage;
import betterquesting.api2.client.gui.controls.PanelTextField;
import betterquesting.api2.client.gui.controls.filters.FieldFilterString;
import betterquesting.api2.client.gui.events.IPEventListener;
import betterquesting.api2.client.gui.events.PEventBroadcaster;
import betterquesting.api2.client.gui.events.PanelEvent;
import betterquesting.api2.client.gui.events.types.PEventButton;
import betterquesting.api2.client.gui.misc.*;
import betterquesting.api2.client.gui.panels.CanvasTextured;
import betterquesting.api2.client.gui.panels.bars.PanelVScrollBar;
import betterquesting.api2.client.gui.panels.content.PanelLine;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.panels.lists.CanvasScrolling;
import betterquesting.api2.client.gui.panels.lists.CanvasSearch;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetLine;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.registry.IFactoryData;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.client.gui2.editors.nbt.GuiNbtEditor;
import betterquesting.network.handlers.NetQuestEdit;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.rewards.RewardRegistry;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class GuiRewardEditor extends GuiScreenCanvas implements IPEventListener, IVolatileScreen, INeedsRefresh
{
	private CanvasScrolling qrList;
	
    private IQuest quest;
    private final int qID;
    
    public GuiRewardEditor(Screen parent, IQuest quest)
    {
        super(parent);
        
        this.quest = quest;
        this.qID = QuestDatabase.INSTANCE.getID(quest);
    }
	
	@Override
	public void refreshGui()
	{
	    quest = QuestDatabase.INSTANCE.getValue(qID);
	    
	    if(quest == null)
        {
            minecraft.displayGuiScreen(this.parent);
            return;
        }
        
        refreshRewards();
    }
    
    @Override
    public void initPanel()
    {
        super.initPanel();
	    
	    if(qID < 0)
        {
            minecraft.displayGuiScreen(this.parent);
            return;
        }
		
		PEventBroadcaster.INSTANCE.register(this, PEventButton.class);
        
        // Background panel
        CanvasTextured cvBackground = new CanvasTextured(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0), PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(cvBackground);
        
        PanelTextBox panTxt = new PanelTextBox(new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(0, 16, 0, -32), 0), QuestTranslation.translate("betterquesting.title.edit_rewards")).setAlignment(1);
        panTxt.setColor(PresetColor.TEXT_HEADER.getColor());
        cvBackground.addPanel(panTxt);
        
        cvBackground.addPanel(new PanelButton(new GuiTransform(GuiAlign.BOTTOM_CENTER, -100, -16, 200, 16, 0), 0, QuestTranslation.translate("gui.back")));
    
        CanvasSearch<IFactoryData<IReward, CompoundNBT>, IFactoryData<IReward, CompoundNBT>> cvRegSearch = new CanvasSearch<IFactoryData<IReward, CompoundNBT>, IFactoryData<IReward, CompoundNBT>>((new GuiTransform(GuiAlign.HALF_RIGHT, new GuiPadding(8, 48, 24, 32), 0)))
        {
            @Override
            protected Iterator<IFactoryData<IReward, CompoundNBT>> getIterator()
            {
                List<IFactoryData<IReward, CompoundNBT>> list = RewardRegistry.INSTANCE.getAll();
                list.sort(Comparator.comparing(o -> o.getRegistryName().toString().toLowerCase()));
                return list.iterator();
            }
    
            @Override
            protected void queryMatches(IFactoryData<IReward, CompoundNBT> value, String query, ArrayDeque<IFactoryData<IReward, CompoundNBT>> results)
            {
                if(value.getRegistryName().toString().toLowerCase().contains(query.toLowerCase())) results.add(value);
            }
    
            @Override
            protected boolean addResult(IFactoryData<IReward, CompoundNBT> entry, int index, int cachedWidth)
            {
                this.addPanel(new PanelButtonStorage<>(new GuiRectangle(0, index * 16, cachedWidth, 16, 0), 1, entry.getRegistryName().toString(), entry));
                return true;
            }
        };
        cvBackground.addPanel(cvRegSearch);
        
        PanelVScrollBar scReg = new PanelVScrollBar(new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(-24, 48, 16, 32), 0));
        cvBackground.addPanel(scReg);
        cvRegSearch.setScrollDriverY(scReg);
        
        PanelTextField<String> tfSearch = new PanelTextField<>(new GuiTransform(new GuiAnchor(0.5F, 0F, 1F, 0F), new GuiPadding(8, 32, 16, -48), 0), "", FieldFilterString.INSTANCE);
        tfSearch.setCallback(cvRegSearch::setSearchFilter);
        tfSearch.setWatermark("Search...");
        cvBackground.addPanel(tfSearch);
        
        qrList = new CanvasScrolling(new GuiTransform(GuiAlign.HALF_LEFT, new GuiPadding(16, 32, 16, 32), 0));
        cvBackground.addPanel(qrList);
        
        PanelVScrollBar scRew = new PanelVScrollBar(new GuiTransform(new GuiAnchor(0.5F, 0F, 0.5F, 1F), new GuiPadding(-16, 32, 8, 32), 0));
        cvBackground.addPanel(scRew);
        qrList.setScrollDriverY(scRew);
		
        // === DIVIDERS ===
        
		IGuiRect ls0 = new GuiTransform(GuiAlign.TOP_CENTER, 0, 32, 0, 0, 0);
		ls0.setParent(cvBackground.getTransform());
		IGuiRect le0 = new GuiTransform(GuiAlign.BOTTOM_CENTER, 0, -32, 0, 0, 0);
		le0.setParent(cvBackground.getTransform());
		PanelLine paLine0 = new PanelLine(ls0, le0, PresetLine.GUI_DIVIDER.getLine(), 1, PresetColor.GUI_DIVIDER.getColor(), 1);
		cvBackground.addPanel(paLine0);
        
        refreshRewards();
    }
	
	@Override
	public void onPanelEvent(PanelEvent event)
	{
		if(event instanceof PEventButton)
		{
			onButtonPress((PEventButton)event);
		}
	}
	
	@SuppressWarnings("unchecked")
	private void onButtonPress(PEventButton event)
	{
        IPanelButton btn = event.getButton();
        
        if(btn.getButtonID() == 0) // Exit
        {
            minecraft.displayGuiScreen(this.parent);
        } else if(btn.getButtonID() ==  1 && btn instanceof PanelButtonStorage) // Add
        {
            IFactoryData<IReward, CompoundNBT> fact = ((PanelButtonStorage<IFactoryData<IReward, CompoundNBT>>)btn).getStoredValue();
            quest.getRewards().add(quest.getRewards().nextID(), fact.createNew());
            
            SendChanges();
        } else if(btn.getButtonID() == 2 && btn instanceof PanelButtonStorage) // Remove
        {
            IReward reward = ((PanelButtonStorage<IReward>)btn).getStoredValue();
            
            if(quest.getRewards().removeValue(reward))
            {
                SendChanges();
            }
        } else if(btn.getButtonID() == 3 && btn instanceof PanelButtonStorage) // Edit
        {
            IReward reward = ((PanelButtonStorage<IReward>)btn).getStoredValue();
            Screen editor = reward.getRewardEditor(this, new DBEntry<>(qID, quest));
            
            if(editor != null)
            {
                minecraft.displayGuiScreen(editor);
            } else
            {
                minecraft.displayGuiScreen(new GuiNbtEditor(this, reward.writeToNBT(new CompoundNBT()), value -> {
                    reward.readFromNBT(value);
                    SendChanges();
                }));
            }
        }
    }
    
    private void refreshRewards()
    {
        List<DBEntry<IReward>> dbRew = quest.getRewards().getEntries();
        
        qrList.resetCanvas();
        int w = qrList.getTransform().getWidth();
        
        for(int i = 0; i < dbRew.size(); i++)
        {
            IReward reward = dbRew.get(i).getValue();
            qrList.addPanel(new PanelButtonStorage<>(new GuiRectangle(0, i * 16, w - 16, 16, 0), 3, QuestTranslation.translate(reward.getUnlocalisedName()), reward));
            qrList.addPanel(new PanelButtonStorage<>(new GuiRectangle(w - 16, i * 16, 16, 16, 0), 2, "" + TextFormatting.RED + TextFormatting.BOLD + "x", reward));
        }
    }
	
	private void SendChanges()
	{
	    CompoundNBT payload = new CompoundNBT();
	    ListNBT dataList = new ListNBT();
	    CompoundNBT entry = new CompoundNBT();
	    entry.putInt("questID", qID);
	    entry.put("config", quest.writeToNBT(new CompoundNBT()));
	    dataList.add(entry);
	    payload.put("data", dataList);
	    payload.putInt("action", 0);
	    NetQuestEdit.sendEdit(payload);
	}
}
