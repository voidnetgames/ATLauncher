/*
 * ATLauncher - https://github.com/ATLauncher/ATLauncher
 * Copyright (C) 2013-2022 ATLauncher
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.atlauncher.gui.dialogs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;

import org.mini2Dx.gettext.GetText;

import com.atlauncher.App;
import com.atlauncher.builders.HTMLBuilder;
import com.atlauncher.constants.Constants;
import com.atlauncher.data.AddModRestriction;
import com.atlauncher.data.DisableableMod;
import com.atlauncher.data.Instance;
import com.atlauncher.data.ModManagement;
import com.atlauncher.data.ModPlatform;
import com.atlauncher.data.curseforge.CurseForgeCategoryForGame;
import com.atlauncher.data.curseforge.CurseForgeProject;
import com.atlauncher.data.minecraft.loaders.LoaderVersion;
import com.atlauncher.data.modrinth.ModrinthCategory;
import com.atlauncher.data.modrinth.ModrinthProject;
import com.atlauncher.data.modrinth.ModrinthSearchHit;
import com.atlauncher.data.modrinth.ModrinthSearchResult;
import com.atlauncher.exceptions.InvalidMinecraftVersion;
import com.atlauncher.gui.card.CurseForgeProjectCard;
import com.atlauncher.gui.card.ModrinthSearchHitCard;
import com.atlauncher.gui.layouts.WrapLayout;
import com.atlauncher.gui.panels.LoadingPanel;
import com.atlauncher.gui.panels.NoCurseModsPanel;
import com.atlauncher.managers.ConfigManager;
import com.atlauncher.managers.DialogManager;
import com.atlauncher.managers.LogManager;
import com.atlauncher.managers.MinecraftManager;
import com.atlauncher.network.Analytics;
import com.atlauncher.network.analytics.AnalyticsEvent;
import com.atlauncher.utils.ComboItem;
import com.atlauncher.utils.CurseForgeApi;
import com.atlauncher.utils.ModrinthApi;
import com.atlauncher.utils.OS;
import com.atlauncher.utils.Utils;
import com.formdev.flatlaf.icons.FlatSearchIcon;

public final class AddModsDialog extends JDialog {
    private final ModManagement instanceOrServer;

    private boolean updating = false;

    private final JPanel contentPanel = new JPanel(new WrapLayout());
    private final JPanel topPanel = new JPanel(new BorderLayout());
    private final JPanel warningPanel = new JPanel();
    private final JTextField searchField = new JTextField(16);
    private final JLabel platformMessageLabel = new JLabel();
    private final JComboBox<ComboItem<ModPlatform>> hostComboBox = new JComboBox<>();
    private final JComboBox<ComboItem<String>> sectionComboBox = new JComboBox<>();
    private final JComboBox<ComboItem<String>> sortComboBox = new JComboBox<>();
    private final JComboBox<ComboItem<String>> categoriesComboBox = new JComboBox<>();

    // #. {0} is the loader api (Fabric API/QSL)
    private final JButton installFabricApiButton = new JButton(GetText.tr("Install {0}", "Fabric API"));

    private final JLabel fabricApiWarningLabel = new JLabel(
        "<html><p align=\"center\" style=\"color: "
            + String.format("#%06x", 0xFFFFFF & UIManager.getColor("yellow").getRGB())
            // #. {0} is the loader (Fabric/Quilt), {1} is the loader api (Fabric API/QSL)
            + "\">" + GetText.tr("Before installing {0} mods, you should install {1} first!",
            "Fabric", "Fabric API")
            + "</p></html>");

    // #. {0} is the loader api (Fabric API/QSL)
    private final JButton installLegacyFabricApiButton = new JButton(GetText.tr("Install {0}", "Legacy Fabric API"));

    private final JLabel legacyFabricApiWarningLabel = new JLabel(
        "<html><p align=\"center\" style=\"color: "
            + String.format("#%06x", 0xFFFFFF & UIManager.getColor("yellow").getRGB())
            // #. {0} is the loader (Fabric/Quilt), {1} is the loader api (Fabric API/QSL)
            + "\">" + GetText.tr("Before installing {0} mods, you should install {1} first!",
            "Legacy Fabric", "Legacy Fabric API")
            + "</p></html>");

    private final JEditorPane legacyFabricModrinthWarningLabel = new JEditorPane("text/html",
        "<html><p align=\"center\" style=\"color: "
            + String.format("#%06x", 0xFFFFFF & UIManager.getColor("yellow").getRGB())
            + "\">"
            + GetText.tr(
            "Modrinth doesn't support filtering accurately for Legacy Fabric, so mods shown may not be installable. Consider using CurseForge or visit <a href=\"https://legacyfabric.net/mods.html\">https://legacyfabric.net/mods.html</a> for a list of compatable mods.")
            + "</p></html>");

    private final JButton installQuiltStandardLibrariesButton = new JButton(
        // #. {0} is the loader api (Fabric API/QSL)
        GetText.tr("Install {0}", "Quilt Standard Libraries"));

    private final JLabel quiltStandardLibrariesWarningLabel = new JLabel(
        "<html><p align=\"center\" style=\"color: "
            + String.format("#%06x", 0xFFFFFF & UIManager.getColor("yellow").getRGB())
            + "\">"
            // #. {0} is the loader (Fabric/Quilt), {1} is the loader api (Fabric API/QSL)
            + GetText.tr("Before installing {0} mods, you should install {1} first!",
            "Quilt", "Quilt Standard Libraries")
            + "</p></html>");

    // #. {0} is the loader api (Fabric API/QSL)
    private final JButton installForgifiedFabricApiButton = new JButton(
        GetText.tr("Install {0}", "Forgified Fabric API"));

    private final JLabel forgifiedFabricApiWarningLabel = new JLabel(
        "<html><p align=\"center\" style=\"color: "
            + String.format("#%06x", 0xFFFFFF & UIManager.getColor("yellow").getRGB())
            // #. {0} is the loader (Fabric/Quilt), {1} is the loader api (Fabric API/QSL)
            + "\">" + GetText.tr("Before installing {0} mods, you should install {1} first!",
            "Fabric", "Forgified Fabric API")
            + "</p></html>");

    private JScrollPane jscrollPane;
    private JButton nextButton;
    private JButton prevButton;
    private final JPanel mainPanel = new JPanel(new BorderLayout());
    private int page = 0;

    public AddModsDialog(ModManagement instanceOrServer) {
        this(App.launcher.getParent(), instanceOrServer);
    }

    public AddModsDialog(Window parent, ModManagement instanceOrServer) {
        // #. {0} is the name of the mod we're installing
        super(parent, GetText.tr("Adding Mods For {0}", instanceOrServer.getName()), ModalityType.DOCUMENT_MODAL);
        this.instanceOrServer = instanceOrServer;

        this.setPreferredSize(new Dimension(800, 500));
        this.setMinimumSize(new Dimension(800, 500));
        this.setResizable(true);
        this.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        int selectedHostIndex = 0;
        String platformMessage = null;

        if (ConfigManager.getConfigItem("platforms.curseforge.modsEnabled", true)) {
            hostComboBox.addItem(new ComboItem<>(ModPlatform.CURSEFORGE, "CurseForge"));

            if (App.settings.defaultModPlatform == ModPlatform.CURSEFORGE) {
                selectedHostIndex = hostComboBox.getItemCount() - 1;
                platformMessage = ConfigManager.getConfigItem("platforms.curseforge.message", null);
            }
        }

        if (ConfigManager.getConfigItem("platforms.modrinth.modsEnabled", true)) {
            hostComboBox.addItem(new ComboItem<>(ModPlatform.MODRINTH, "Modrinth"));

            if (App.settings.defaultModPlatform == ModPlatform.MODRINTH) {
                selectedHostIndex = hostComboBox.getItemCount() - 1;
                platformMessage = ConfigManager.getConfigItem("platforms.modrinth.message", null);
            }
        }

        hostComboBox.setSelectedIndex(selectedHostIndex);

        searchField.putClientProperty("JTextField.placeholderText", GetText.tr("Search"));
        searchField.putClientProperty("JTextField.leadingIcon", new FlatSearchIcon());
        searchField.putClientProperty("JTextField.showClearButton", true);
        searchField.putClientProperty("JTextField.clearCallback", (Runnable) () -> {
            searchField.setText("");
            searchForMods();
        });

        if (platformMessage != null) {
            platformMessageLabel.setText(new HTMLBuilder().center().text(platformMessage).build());
        }
        platformMessageLabel.setVisible(platformMessage != null);

        addSectionAndSortOptions(true);

        addCategories();

        setupComponents();

        this.loadDefaultMods();

        this.pack();
        this.setLocationRelativeTo(parent);
    }

    private void setupComponents() {
        Analytics.sendScreenView("Add Mods Dialog");

        this.topPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        this.warningPanel.setLayout(new BoxLayout(this.warningPanel, BoxLayout.X_AXIS));

        JPanel searchButtonsPanel = new JPanel();

        searchButtonsPanel.setLayout(new BoxLayout(searchButtonsPanel, BoxLayout.X_AXIS));
        searchButtonsPanel.add(this.hostComboBox);
        searchButtonsPanel.add(Box.createHorizontalStrut(5));
        searchButtonsPanel.add(this.sectionComboBox);
        searchButtonsPanel.add(Box.createHorizontalStrut(5));
        searchButtonsPanel.add(this.sortComboBox);
        searchButtonsPanel.add(Box.createHorizontalStrut(5));
        searchButtonsPanel.add(this.categoriesComboBox);
        searchButtonsPanel.add(Box.createHorizontalStrut(20));
        searchButtonsPanel.add(this.searchField);

        this.installFabricApiButton.addActionListener(e -> {
            ModPlatform selectedHost = ((ComboItem<ModPlatform>) hostComboBox.getSelectedItem()).getValue();
            boolean isCurseForge = selectedHost == ModPlatform.CURSEFORGE;
            if (isCurseForge) {
                final ProgressDialog<CurseForgeProject> curseForgeProjectLookupDialog = new ProgressDialog<>(
                    // #. {0} is the loader api were getting info from (Fabric/Quilt)
                    GetText.tr("Getting {0} Information", "Fabric API"), 0,
                    // #. {0} is the loader api were getting info from (Fabric/Quilt)
                    GetText.tr("Getting {0} Information", "Fabric API"),
                    "Aborting Getting Fabric API Information");

                curseForgeProjectLookupDialog.addThread(new Thread(() -> {
                    curseForgeProjectLookupDialog
                        .setReturnValue(CurseForgeApi.getProjectById(Constants.CURSEFORGE_FABRIC_MOD_ID));

                    curseForgeProjectLookupDialog.close();
                }));

                curseForgeProjectLookupDialog.start();

                CurseForgeProject mod = curseForgeProjectLookupDialog.getReturnValue();

                if (mod == null) {
                    // #. {0} is the loader api were getting info from (Fabric/Quilt)
                    DialogManager.okDialog().setTitle(GetText.tr("Error Getting {0} Information", "Fabric API"))
                        // #. {0} is the loader (Fabric/Quilt) {1} is the platform (CurseForge/Modrinth)
                        .setContent(new HTMLBuilder().center().text(GetText.tr(
                                "There was an error getting {0} information from {1}. Please try again later.",
                                "Fabric API", "CurseForge"))
                            .build())
                        .setType(DialogManager.ERROR).show();
                    return;
                }

                Analytics.trackEvent(AnalyticsEvent.forAddMod("Fabric API", "CurseForge", "mod"));
                CurseForgeProjectFileSelectorDialog curseForgeProjectFileSelectorDialog = new CurseForgeProjectFileSelectorDialog(
                    this, mod, instanceOrServer);
                curseForgeProjectFileSelectorDialog.setVisible(true);

                if (instanceOrServer.getMods().stream().anyMatch(
                    m -> (m.isFromCurseForge() && m.getCurseForgeModId() == Constants.CURSEFORGE_FABRIC_MOD_ID)
                        || (m.isFromModrinth()
                        && m.modrinthProject.id.equalsIgnoreCase(Constants.MODRINTH_FABRIC_MOD_ID)))) {
                    fabricApiWarningLabel.setVisible(false);
                    installFabricApiButton.setVisible(false);
                }
            } else {
                final ProgressDialog<ModrinthProject> modrinthProjectLookupDialog = new ProgressDialog<>(
                    // #. {0} is the loader api were getting info from (Fabric/Quilt)
                    GetText.tr("Getting {0} Information", "Fabric API"), 0,
                    // #. {0} is the loader api were getting info from (Fabric/Quilt)
                    GetText.tr("Getting {0} Information", "Fabric API"),
                    "Aborting Getting Fabric API Information");

                modrinthProjectLookupDialog.addThread(new Thread(() -> {
                    modrinthProjectLookupDialog
                        .setReturnValue(ModrinthApi.getProject(Constants.MODRINTH_FABRIC_MOD_ID));

                    modrinthProjectLookupDialog.close();
                }));

                modrinthProjectLookupDialog.start();

                ModrinthProject mod = modrinthProjectLookupDialog.getReturnValue();

                if (mod == null) {
                    // #. {0} is the loader api were getting info from (Fabric/Quilt)
                    DialogManager.okDialog().setTitle(GetText.tr("Error Getting {0} Information", "Fabric API"))
                        // #. {0} is the loader (Fabric/Quilt) {1} is the platform (CurseForge/Modrinth)
                        .setContent(new HTMLBuilder().center().text(GetText.tr(
                                "There was an error getting {0} information from {1}. Please try again later.",
                                "Fabric API",
                                "Modrinth"))
                            .build())
                        .setType(DialogManager.ERROR).show();
                    return;
                }

                Analytics.trackEvent(AnalyticsEvent.forAddMod("Fabric API", "Modrinth", "mod"));
                ModrinthVersionSelectorDialog modrinthVersionSelectorDialog = new ModrinthVersionSelectorDialog(this,
                    mod, instanceOrServer);
                modrinthVersionSelectorDialog.setVisible(true);

                if (instanceOrServer.getMods().stream().anyMatch(
                    m -> (m.isFromCurseForge() && m.getCurseForgeModId() == Constants.CURSEFORGE_FABRIC_MOD_ID)
                        || (m.isFromModrinth()
                        && m.modrinthProject.id.equalsIgnoreCase(Constants.MODRINTH_FABRIC_MOD_ID)))) {
                    fabricApiWarningLabel.setVisible(false);
                    installFabricApiButton.setVisible(false);
                }
            }

            if (searchField.getText().isEmpty()) {
                loadDefaultMods();
            } else {
                searchForMods();
            }
        });

        this.installLegacyFabricApiButton.addActionListener(e -> {
            boolean isCurseForge = ((ComboItem<ModPlatform>) hostComboBox.getSelectedItem())
                .getValue() == ModPlatform.CURSEFORGE;
            if (isCurseForge) {
                final ProgressDialog<CurseForgeProject> curseForgeProjectLookupDialog = new ProgressDialog<>(
                    // #. {0} is the loader api were getting info from (Fabric/Quilt)
                    GetText.tr("Getting {0} Information", "Legacy Fabric API"), 0,
                    // #. {0} is the loader api were getting info from (Fabric/Quilt)
                    GetText.tr("Getting {0} Information", "Legacy Fabric API"),
                    "Aborting Getting Legacy Fabric API Information");

                curseForgeProjectLookupDialog.addThread(new Thread(() -> {
                    curseForgeProjectLookupDialog
                        .setReturnValue(CurseForgeApi.getProjectById(Constants.CURSEFORGE_LEGACY_FABRIC_MOD_ID));

                    curseForgeProjectLookupDialog.close();
                }));

                curseForgeProjectLookupDialog.start();

                CurseForgeProject mod = curseForgeProjectLookupDialog.getReturnValue();

                if (mod == null) {
                    // #. {0} is the loader api were getting info from (Fabric/Quilt)
                    DialogManager.okDialog().setTitle(GetText.tr("Error Getting {0} Information", "Legacy Fabric API"))
                        // #. {0} is the loader (Fabric/Quilt) {1} is the platform (CurseForge/Modrinth)
                        .setContent(new HTMLBuilder().center().text(GetText.tr(
                                "There was an error getting {0} information from {1}. Please try again later.",
                                "Legacy Fabric API",
                                "CurseForge"))
                            .build())
                        .setType(DialogManager.ERROR).show();
                    return;
                }

                Analytics.trackEvent(AnalyticsEvent.forAddMod("Legacy Fabric API", "CurseForge", "mod"));
                CurseForgeProjectFileSelectorDialog curseForgeProjectFileSelectorDialog = new CurseForgeProjectFileSelectorDialog(
                    this, mod, instanceOrServer);
                curseForgeProjectFileSelectorDialog.setVisible(true);

                if (instanceOrServer.getMods().stream().anyMatch(
                    m -> (m.isFromCurseForge()
                        && m.getCurseForgeModId() == Constants.CURSEFORGE_LEGACY_FABRIC_MOD_ID)
                        || (m.isFromModrinth()
                        && m.modrinthProject.id
                        .equalsIgnoreCase(Constants.MODRINTH_LEGACY_FABRIC_MOD_ID)))) {
                    legacyFabricApiWarningLabel.setVisible(false);
                    installLegacyFabricApiButton.setVisible(false);
                }
            } else {
                final ProgressDialog<ModrinthProject> modrinthProjectLookupDialog = new ProgressDialog<>(
                    // #. {0} is the loader api were getting info from (Fabric/Quilt)
                    GetText.tr("Getting {0} Information", "Legacy Fabric API"), 0,
                    // #. {0} is the loader api were getting info from (Fabric/Quilt)
                    GetText.tr("Getting {0} Information", "Legacy Fabric API"),
                    "Aborting Getting Legacy Fabric API Information");

                modrinthProjectLookupDialog.addThread(new Thread(() -> {
                    modrinthProjectLookupDialog
                        .setReturnValue(ModrinthApi.getProject(Constants.MODRINTH_LEGACY_FABRIC_MOD_ID));

                    modrinthProjectLookupDialog.close();
                }));

                modrinthProjectLookupDialog.start();

                ModrinthProject mod = modrinthProjectLookupDialog.getReturnValue();

                if (mod == null) {
                    // #. {0} is the loader api were getting info from (Fabric/Quilt)
                    DialogManager.okDialog().setTitle(GetText.tr("Error Getting {0} Information", "Legacy Fabric API"))
                        // #. {0} is the loader (Fabric/Quilt) {1} is the platform (CurseForge/Modrinth)
                        .setContent(new HTMLBuilder().center().text(GetText.tr(
                                "There was an error getting {0} information from {1}. Please try again later.",
                                "Legacy Fabric API",
                                "Modrinth"))
                            .build())
                        .setType(DialogManager.ERROR).show();
                    return;
                }

                Analytics.trackEvent(AnalyticsEvent.forAddMod("Legacy Fabric API", "Modrinth", "mod"));
                ModrinthVersionSelectorDialog modrinthVersionSelectorDialog = new ModrinthVersionSelectorDialog(this,
                    mod, instanceOrServer);
                modrinthVersionSelectorDialog.setVisible(true);

                if (instanceOrServer.getMods().stream().anyMatch(
                    m -> (m.isFromCurseForge()
                        && m.getCurseForgeModId() == Constants.CURSEFORGE_LEGACY_FABRIC_MOD_ID)
                        || (m.isFromModrinth()
                        && m.modrinthProject.id
                        .equalsIgnoreCase(Constants.MODRINTH_LEGACY_FABRIC_MOD_ID)))) {
                    legacyFabricApiWarningLabel.setVisible(false);
                    installLegacyFabricApiButton.setVisible(false);
                }
            }

            if (searchField.getText().isEmpty()) {
                loadDefaultMods();
            } else {
                searchForMods();
            }
        });

        this.installQuiltStandardLibrariesButton.addActionListener(e -> {
            final ProgressDialog<ModrinthProject> modrinthProjectLookupDialog = new ProgressDialog<>(
                // #. {0} is the loader api were getting info from (Fabric/Quilt)
                GetText.tr("Getting {0} Information", "Quilt Standard Libaries"), 0,
                // #. {0} is the loader api were getting info from (Fabric/Quilt)
                GetText.tr("Getting {0} Information", "Quilt Standard Libaries"),
                "Aborting Getting Quilt Standard Libaries Information");

            modrinthProjectLookupDialog.addThread(new Thread(() -> {
                modrinthProjectLookupDialog
                    .setReturnValue(ModrinthApi.getProject(Constants.MODRINTH_QSL_MOD_ID));

                modrinthProjectLookupDialog.close();
            }));

            modrinthProjectLookupDialog.start();

            ModrinthProject mod = modrinthProjectLookupDialog.getReturnValue();

            if (mod == null) {
                DialogManager.okDialog()
                    // #. {0} is the loader api were getting info from (Fabric/Quilt)
                    .setTitle(GetText.tr("Error Getting {0} Information", "Quilt Standard Libaries"))
                    // #. {0} is the loader (Fabric/Quilt) {1} is the platform (CurseForge/Modrinth)
                    .setContent(new HTMLBuilder().center().text(GetText.tr(
                            "There was an error getting {0} information from {1}. Please try again later.",
                            "Quilt Standard Libaries", "Modrinth"))
                        .build())
                    .setType(DialogManager.ERROR).show();
                return;
            }

            Analytics.trackEvent(AnalyticsEvent.forAddMod("Quilt Standard Libraries", "Modrinth", "mod"));
            ModrinthVersionSelectorDialog modrinthVersionSelectorDialog = new ModrinthVersionSelectorDialog(this, mod,
                instanceOrServer);
            modrinthVersionSelectorDialog.setVisible(true);

            if (instanceOrServer.getMods().stream().anyMatch(
                m -> m.isFromModrinth()
                    && m.modrinthProject.id.equalsIgnoreCase(Constants.MODRINTH_QSL_MOD_ID))) {
                quiltStandardLibrariesWarningLabel.setVisible(false);
                installQuiltStandardLibrariesButton.setVisible(false);
            }
        });

        this.installForgifiedFabricApiButton.addActionListener(e -> {
            boolean isCurseForge = ((ComboItem<ModPlatform>) hostComboBox.getSelectedItem())
                .getValue() == ModPlatform.CURSEFORGE;
            if (isCurseForge) {
                final ProgressDialog<CurseForgeProject> curseForgeProjectLookupDialog = new ProgressDialog<>(
                    // #. {0} is the loader api were getting info from (Fabric/Quilt)
                    GetText.tr("Getting {0} Information", "Forgified Fabric API"), 0,
                    // #. {0} is the loader api were getting info from (Fabric/Quilt)
                    GetText.tr("Getting {0} Information", "Forgified Fabric API"),
                    "Aborting Getting Forgified Fabric API Information");

                curseForgeProjectLookupDialog.addThread(new Thread(() -> {
                    curseForgeProjectLookupDialog
                        .setReturnValue(
                            CurseForgeApi.getProjectById(Constants.CURSEFORGE_FORGIFIED_FABRIC_API_MOD_ID));

                    curseForgeProjectLookupDialog.close();
                }));

                curseForgeProjectLookupDialog.start();

                CurseForgeProject mod = curseForgeProjectLookupDialog.getReturnValue();

                if (mod == null) {
                    // #. {0} is the loader api were getting info from (Fabric/Quilt)
                    DialogManager.okDialog()
                        .setTitle(GetText.tr("Error Getting {0} Information", "Forgified Fabric API"))
                        // #. {0} is the loader (Fabric/Quilt) {1} is the platform (CurseForge/Modrinth)
                        .setContent(new HTMLBuilder().center().text(GetText.tr(
                                "There was an error getting {0} information from {1}. Please try again later.",
                                "Forgified Fabric API", "CurseForge"))
                            .build())
                        .setType(DialogManager.ERROR).show();
                    return;
                }

                Analytics.trackEvent(AnalyticsEvent.forAddMod("Forgified Fabric API", "CurseForge", "mod"));
                CurseForgeProjectFileSelectorDialog curseForgeProjectFileSelectorDialog = new CurseForgeProjectFileSelectorDialog(
                    this, mod, instanceOrServer);
                curseForgeProjectFileSelectorDialog.setVisible(true);

                if (instanceOrServer.getMods().stream().anyMatch(
                    m -> (m.isFromCurseForge()
                        && m.getCurseForgeModId() == Constants.CURSEFORGE_FORGIFIED_FABRIC_API_MOD_ID)
                        || (m.isFromModrinth()
                        && m.modrinthProject.id
                        .equalsIgnoreCase(Constants.MODRINTH_FORGIFIED_FABRIC_API_MOD_ID)))) {
                    forgifiedFabricApiWarningLabel.setVisible(false);
                    installForgifiedFabricApiButton.setVisible(false);
                }
            } else {
                final ProgressDialog<ModrinthProject> modrinthProjectLookupDialog = new ProgressDialog<>(
                    // #. {0} is the loader api were getting info from (Fabric/Quilt)
                    GetText.tr("Getting {0} Information", "Fabric API"), 0,
                    // #. {0} is the loader api were getting info from (Fabric/Quilt)
                    GetText.tr("Getting {0} Information", "Fabric API"),
                    "Aborting Getting Fabric API Information");

                modrinthProjectLookupDialog.addThread(new Thread(() -> {
                    modrinthProjectLookupDialog
                        .setReturnValue(ModrinthApi.getProject(Constants.MODRINTH_FORGIFIED_FABRIC_API_MOD_ID));

                    modrinthProjectLookupDialog.close();
                }));

                modrinthProjectLookupDialog.start();

                ModrinthProject mod = modrinthProjectLookupDialog.getReturnValue();

                if (mod == null) {
                    // #. {0} is the loader api were getting info from (Fabric/Quilt)
                    DialogManager.okDialog()
                        .setTitle(GetText.tr("Error Getting {0} Information", "Forgified Fabric API"))
                        // #. {0} is the loader (Fabric/Quilt) {1} is the platform (CurseForge/Modrinth)
                        .setContent(new HTMLBuilder().center().text(GetText.tr(
                                "There was an error getting {0} information from {1}. Please try again later.",
                                "Forgified Fabric API", "Modrinth"))
                            .build())
                        .setType(DialogManager.ERROR).show();
                    return;
                }

                Analytics.trackEvent(AnalyticsEvent.forAddMod("Forgified Fabric API", "Modrinth", "mod"));
                ModrinthVersionSelectorDialog modrinthVersionSelectorDialog = new ModrinthVersionSelectorDialog(this,
                    mod, instanceOrServer);
                modrinthVersionSelectorDialog.setVisible(true);

                if (instanceOrServer.getMods().stream().anyMatch(
                    m -> (m.isFromCurseForge()
                        && m.getCurseForgeModId() == Constants.CURSEFORGE_FORGIFIED_FABRIC_API_MOD_ID)
                        || (m.isFromModrinth()
                        && m.modrinthProject.id
                        .equalsIgnoreCase(Constants.MODRINTH_FORGIFIED_FABRIC_API_MOD_ID)))) {
                    forgifiedFabricApiWarningLabel.setVisible(false);
                    installForgifiedFabricApiButton.setVisible(false);
                }
            }

            if (searchField.getText().isEmpty()) {
                loadDefaultMods();
            } else {
                searchForMods();
            }
        });

        LoaderVersion loaderVersion = instanceOrServer.getLoaderVersion();

        if (loaderVersion != null && loaderVersion.isFabric() && instanceOrServer.getMods().stream()
            .noneMatch(m -> (m.isFromCurseForge() && m.getCurseForgeModId() == Constants.CURSEFORGE_FABRIC_MOD_ID)
                || (m.isFromModrinth()
                && m.modrinthProject.id.equalsIgnoreCase(Constants.MODRINTH_FABRIC_MOD_ID)))) {
            this.warningPanel.add(fabricApiWarningLabel);
            this.warningPanel.add(Box.createHorizontalGlue());
            this.warningPanel.add(installFabricApiButton);
        }

        if (loaderVersion != null && loaderVersion.isLegacyFabric() && instanceOrServer.getMods().stream()
            .noneMatch(m -> (m.isFromCurseForge()
                && m.getCurseForgeModId() == Constants.CURSEFORGE_LEGACY_FABRIC_MOD_ID)
                || (m.isFromModrinth()
                && m.modrinthProject.id.equalsIgnoreCase(Constants.MODRINTH_LEGACY_FABRIC_MOD_ID)))) {
            this.warningPanel.add(legacyFabricApiWarningLabel);
            this.warningPanel.add(Box.createHorizontalGlue());
            this.warningPanel.add(installLegacyFabricApiButton);
        }

        if (loaderVersion != null && loaderVersion.isLegacyFabric()) {
            this.warningPanel.add(legacyFabricModrinthWarningLabel);
        }

        if (loaderVersion != null && loaderVersion.isQuilt() && instanceOrServer.getMods().stream()
            .noneMatch(m -> m.isFromModrinth()
                && m.modrinthProject.id.equalsIgnoreCase(Constants.MODRINTH_QSL_MOD_ID))) {
            this.warningPanel.add(quiltStandardLibrariesWarningLabel);
            this.warningPanel.add(Box.createHorizontalGlue());
            this.warningPanel.add(installQuiltStandardLibrariesButton);
        }

        // If on Forge/NeoForge and has Sinytra Connector installed, then show Forgified
        // Fabric API things
        if (instanceOrServer.isForgeLikeAndHasInstalledSinytraConnector() && instanceOrServer.getMods().stream()
            .noneMatch(m -> (m.isFromCurseForge()
                && m.getCurseForgeModId() == Constants.CURSEFORGE_FORGIFIED_FABRIC_API_MOD_ID)
                || (m.isFromModrinth()
                && m.modrinthProject.id
                .equalsIgnoreCase(Constants.MODRINTH_FORGIFIED_FABRIC_API_MOD_ID)))) {
            this.warningPanel.add(forgifiedFabricApiWarningLabel);
            this.warningPanel.add(Box.createHorizontalGlue());
            this.warningPanel.add(installForgifiedFabricApiButton);
        }

        this.topPanel.add(searchButtonsPanel, BorderLayout.NORTH);
        this.topPanel.add(warningPanel, BorderLayout.CENTER);

        this.jscrollPane = new JScrollPane(this.contentPanel) {
            {
                this.getVerticalScrollBar().setUnitIncrement(16);
            }
        };

        this.jscrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        mainPanel.add(this.topPanel, BorderLayout.NORTH);
        mainPanel.add(this.jscrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel bottomButtonsPanel = new JPanel(new FlowLayout());

        prevButton = new JButton("<<");
        prevButton.setEnabled(false);
        prevButton.addActionListener(e -> goToPreviousPage());

        nextButton = new JButton(">>");
        nextButton.setEnabled(false);
        nextButton.addActionListener(e -> goToNextPage());

        bottomButtonsPanel.add(prevButton);
        bottomButtonsPanel.add(nextButton);

        legacyFabricModrinthWarningLabel.setVisible(((ComboItem<ModPlatform>) hostComboBox.getSelectedItem())
            .getValue() == ModPlatform.MODRINTH);
        legacyFabricModrinthWarningLabel.setEditable(false);
        legacyFabricModrinthWarningLabel.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                OS.openWebBrowser(e.getURL());
            }
        });

        platformMessageLabel.setForeground(UIManager.getColor("yellow"));
        bottomPanel.add(platformMessageLabel, BorderLayout.NORTH);
        bottomPanel.add(bottomButtonsPanel, BorderLayout.CENTER);

        this.add(mainPanel, BorderLayout.CENTER);
        this.add(bottomPanel, BorderLayout.SOUTH);

        this.hostComboBox.addActionListener(e -> {
            updating = true;
            page = 0;
            ModPlatform selectedModPlatform = ((ComboItem<ModPlatform>) hostComboBox.getSelectedItem()).getValue();

            boolean isCurseForge = selectedModPlatform == ModPlatform.CURSEFORGE;
            boolean isModrinth = selectedModPlatform == ModPlatform.MODRINTH;

            String platformMessage = null;

            sortComboBox.removeAllItems();
            sectionComboBox.removeAllItems();

            addSectionAndSortOptions(false);

            if (isCurseForge) {
                platformMessage = ConfigManager.getConfigItem("platforms.curseforge.message", null);
            } else if (isModrinth) {
                platformMessage = ConfigManager.getConfigItem("platforms.modrinth.message", null);
            }

            addCategories();

            if (platformMessage != null) {
                platformMessageLabel.setText(new HTMLBuilder().center().text(platformMessage).build());
            }
            platformMessageLabel.setVisible(platformMessage != null);
            legacyFabricModrinthWarningLabel
                .setVisible(loaderVersion != null && loaderVersion.isLegacyFabric() && isModrinth);

            if (searchField.getText().isEmpty()) {
                loadDefaultMods();
            } else {
                searchForMods();
            }
            updating = false;
        });

        this.sectionComboBox.addActionListener(e -> {
            if (!updating) {
                page = 0;

                addCategories();

                if (searchField.getText().isEmpty()) {
                    loadDefaultMods();
                } else {
                    searchForMods();
                }
            }
        });

        this.sortComboBox.addActionListener(e -> {
            if (!updating) {
                page = 0;

                if (searchField.getText().isEmpty()) {
                    loadDefaultMods();
                } else {
                    searchForMods();
                }
            }
        });

        this.categoriesComboBox.addActionListener(e -> {
            if (!updating) {
                page = 0;

                if (searchField.getText().isEmpty()) {
                    loadDefaultMods();
                } else {
                    searchForMods();
                }
            }
        });

        this.searchField.addActionListener(e -> searchForMods());
    }

    private void setLoading(boolean loading) {
        if (loading) {
            contentPanel.removeAll();
            contentPanel.setLayout(new BorderLayout());
            contentPanel.add(new LoadingPanel(), BorderLayout.CENTER);
        }

        revalidate();
        repaint();
    }

    private void goToPreviousPage() {
        if (page > 0) {
            page -= 1;
        }

        ModPlatform selectedModPlatform = ((ComboItem<ModPlatform>) hostComboBox.getSelectedItem()).getValue();
        Analytics.trackEvent(
            AnalyticsEvent.forSearchEventPlatform("add_mods", searchField.getText(), page + 1,
                selectedModPlatform.toString()));

        getMods();
    }

    private void goToNextPage() {
        if (contentPanel.getComponentCount() != 0) {
            page += 1;
        }

        ModPlatform selectedModPlatform = ((ComboItem<ModPlatform>) hostComboBox.getSelectedItem()).getValue();
        Analytics.trackEvent(
            AnalyticsEvent.forSearchEventPlatform("add_mods", searchField.getText(), page + 1,
                selectedModPlatform.toString()));

        getMods();
    }

    @SuppressWarnings("unchecked")
    private void getMods() {
        setLoading(true);
        prevButton.setEnabled(false);
        nextButton.setEnabled(false);

        String query = searchField.getText();
        ModPlatform selectedModPlatform = ((ComboItem<ModPlatform>) hostComboBox.getSelectedItem()).getValue();
        String sortValue =
            Optional.ofNullable((ComboItem<String>) sortComboBox.getSelectedItem()).map(ComboItem::getValue)
                .orElse(selectedModPlatform == ModPlatform.CURSEFORGE ? "Popularity" : "relevance");

        new Thread(() -> {
            if (selectedModPlatform == ModPlatform.CURSEFORGE) {
                String versionToSearchFor = App.settings.addModRestriction == AddModRestriction.STRICT
                    ? instanceOrServer.getMinecraftVersion()
                    : null;

                if (((ComboItem<String>) sectionComboBox.getSelectedItem()).getValue().equals("Resource Packs")) {
                    setCurseForgeMods(CurseForgeApi.searchResourcePacks(query, page,
                        sortValue,
                        categoriesComboBox.getSelectedItem() == null ? null
                            : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                } else if (((ComboItem<String>) sectionComboBox.getSelectedItem()).getValue().equals("Shaders")) {
                    setCurseForgeMods(CurseForgeApi.searchShaderPacks(query, page,
                        sortValue,
                        categoriesComboBox.getSelectedItem() == null ? null
                            : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                } else if (((ComboItem<String>) sectionComboBox.getSelectedItem()).getValue().equals("Worlds")) {
                    setCurseForgeMods(CurseForgeApi.searchWorlds(versionToSearchFor, query, page,
                        sortValue,
                        categoriesComboBox.getSelectedItem() == null ? null
                            : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                } else if (((ComboItem<String>) sectionComboBox.getSelectedItem()).getValue().equals("Plugins")) {
                    setCurseForgeMods(CurseForgeApi.searchPlugins(versionToSearchFor, query, page,
                        sortValue,
                        categoriesComboBox.getSelectedItem() == null ? null
                            : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                } else {
                    if (instanceOrServer.getLoaderVersion().isFabric()
                        || instanceOrServer.getLoaderVersion().isLegacyFabric()) {
                        setCurseForgeMods(CurseForgeApi.searchModsForFabric(versionToSearchFor, query, page,
                            sortValue,
                            categoriesComboBox.getSelectedItem() == null ? null
                                : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                    } else if (instanceOrServer.getLoaderVersion().isQuilt()) {
                        setCurseForgeMods(CurseForgeApi.searchModsForQuilt(versionToSearchFor, query, page,
                            sortValue,
                            categoriesComboBox.getSelectedItem() == null ? null
                                : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                    } else if (instanceOrServer.isForgeLikeAndHasInstalledSinytraConnector()) {
                        if (instanceOrServer.getLoaderVersion().isForge()) {
                            setCurseForgeMods(CurseForgeApi.searchModsForForgeOrFabric(versionToSearchFor, query, page,
                                sortValue,
                                ((ComboItem<String>) categoriesComboBox.getSelectedItem()) == null ? null
                                    : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                        } else {
                            setCurseForgeMods(CurseForgeApi.searchModsForNeoForgeOrFabric(versionToSearchFor, query,
                                page,
                                sortValue,
                                ((ComboItem<String>) categoriesComboBox.getSelectedItem()) == null ? null
                                    : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                        }
                    } else if (instanceOrServer.getLoaderVersion().isForge()) {
                        setCurseForgeMods(CurseForgeApi.searchModsForForge(versionToSearchFor, query, page,
                            sortValue,
                            categoriesComboBox.getSelectedItem() == null ? null
                                : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                    } else if (instanceOrServer.getLoaderVersion().isNeoForge()) {
                        setCurseForgeMods(CurseForgeApi.searchModsForNeoForge(versionToSearchFor, query, page,
                            sortValue,
                            categoriesComboBox.getSelectedItem() == null ? null
                                : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                    } else {
                        setCurseForgeMods(CurseForgeApi.searchMods(versionToSearchFor, query, page,
                            sortValue,
                            categoriesComboBox.getSelectedItem() == null ? null
                                : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                    }
                }
            } else if (selectedModPlatform == ModPlatform.MODRINTH) {
                List<String> versionsToSearchFor = new ArrayList<>();

                if (App.settings.addModRestriction == AddModRestriction.STRICT) {
                    versionsToSearchFor.add(instanceOrServer.getMinecraftVersion());
                } else if (App.settings.addModRestriction == AddModRestriction.LAX) {
                    try {
                        versionsToSearchFor.addAll(MinecraftManager
                            .getMajorMinecraftVersions(instanceOrServer.getMinecraftVersion()).stream()
                            .map(mv -> mv.id).collect(Collectors.toList()));
                    } catch (InvalidMinecraftVersion e) {
                        LogManager.logStackTrace(e);
                        versionsToSearchFor = null;
                    }
                } else if (App.settings.addModRestriction == AddModRestriction.NONE) {
                    versionsToSearchFor = null;
                }

                if (((ComboItem<String>) sectionComboBox.getSelectedItem()).getValue().equals("Resource Packs")) {
                    setModrinthMods(ModrinthApi.searchResourcePacks(versionsToSearchFor, query, page,
                        sortValue,
                        categoriesComboBox.getSelectedItem() == null ? null
                            : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                } else if (((ComboItem<String>) sectionComboBox.getSelectedItem()).getValue().equals("Shaders")) {
                    setModrinthMods(ModrinthApi.searchShaders(versionsToSearchFor, query, page,
                        sortValue,
                        categoriesComboBox.getSelectedItem() == null ? null
                            : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                } else if (((ComboItem<String>) sectionComboBox.getSelectedItem()).getValue().equals("Plugins")) {
                    if (instanceOrServer.getLoaderVersion().isPaper()) {
                        setModrinthMods(ModrinthApi.searchPluginsForPaper(versionsToSearchFor, query, page,
                            sortValue,
                            categoriesComboBox.getSelectedItem() == null ? null
                                : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                    } else if (instanceOrServer.getLoaderVersion().isPurpur()) {
                        setModrinthMods(ModrinthApi.searchPluginsForPurpur(versionsToSearchFor, query, page,
                            sortValue,
                            categoriesComboBox.getSelectedItem() == null ? null
                                : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                    }
                } else {
                    if (instanceOrServer.getLoaderVersion().isFabric()
                        || instanceOrServer.getLoaderVersion().isLegacyFabric()) {
                        setModrinthMods(ModrinthApi.searchModsForFabric(versionsToSearchFor, query, page,
                            sortValue,
                            categoriesComboBox.getSelectedItem() == null ? null
                                : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                    } else if (instanceOrServer.getLoaderVersion().isQuilt()) {
                        setModrinthMods(ModrinthApi.searchModsForQuiltOrFabric(versionsToSearchFor, query, page,
                            sortValue,
                            categoriesComboBox.getSelectedItem() == null ? null
                                : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                    } else if (instanceOrServer.isForgeLikeAndHasInstalledSinytraConnector()) {
                        if (instanceOrServer.getLoaderVersion().isForge()) {
                            setModrinthMods(ModrinthApi.searchModsForForgeOrFabric(versionsToSearchFor, query, page,
                                sortValue,
                                ((ComboItem<String>) categoriesComboBox.getSelectedItem()) == null ? null
                                    : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                        } else {
                            setModrinthMods(ModrinthApi.searchModsForNeoForgeOrFabric(versionsToSearchFor, query, page,
                                sortValue,
                                ((ComboItem<String>) categoriesComboBox.getSelectedItem()) == null ? null
                                    : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                        }
                    } else if (instanceOrServer.getLoaderVersion().isForge()) {
                        setModrinthMods(ModrinthApi.searchModsForForge(versionsToSearchFor, query, page,
                            sortValue,
                            categoriesComboBox.getSelectedItem() == null ? null
                                : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                    } else if (instanceOrServer.getLoaderVersion().isNeoForge()) {
                        setModrinthMods(ModrinthApi.searchModsForNeoForge(versionsToSearchFor, query, page,
                            sortValue,
                            categoriesComboBox.getSelectedItem() == null ? null
                                : ((ComboItem<String>) categoriesComboBox.getSelectedItem()).getValue()));
                    }
                }
            }

            setLoading(false);
        }).start();
    }

    private void loadDefaultMods() {
        getMods();
    }

    private void searchForMods() {
        String query = searchField.getText();

        page = 0;

        ModPlatform selectedModPlatform = ((ComboItem<ModPlatform>) hostComboBox.getSelectedItem()).getValue();
        Analytics.trackEvent(
            AnalyticsEvent.forSearchEventPlatform("add_mods", query, page + 1,
                selectedModPlatform.toString()));

        getMods();
    }

    private void setCurseForgeMods(List<CurseForgeProject> mods) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets.set(2, 2, 2, 2);

        contentPanel.removeAll();

        if (mods == null || mods.isEmpty()) {
            contentPanel.setLayout(new BorderLayout());
            contentPanel.add(new NoCurseModsPanel(!this.searchField.getText().isEmpty()), BorderLayout.CENTER);
        } else {
            prevButton.setEnabled(page > 0);
            nextButton.setEnabled(mods.size() == Constants.CURSEFORGE_PAGINATION_SIZE);

            contentPanel.setLayout(new WrapLayout());

            mods.forEach(mod -> {
                CurseForgeProject castMod = mod;
                String sectionValue =
                    Optional.ofNullable((ComboItem<String>) sectionComboBox.getSelectedItem()).map(ComboItem::getValue)
                        .orElse("Mods");

                contentPanel.add(new CurseForgeProjectCard(castMod, instanceOrServer, e -> {
                    if (sectionValue.equals("Plugins")) {
                        Analytics.trackEvent(AnalyticsEvent.forAddPlugin(castMod));
                    } else if (sectionValue.equals("Resource Packs")) {
                        Analytics.trackEvent(AnalyticsEvent.forAddResourcePack(castMod));
                    } else if (sectionValue.equals("Shaders")) {
                        Analytics.trackEvent(AnalyticsEvent.forAddShaders(castMod));
                    } else {
                        Analytics.trackEvent(AnalyticsEvent.forAddMod(castMod));
                    }

                    CurseForgeProjectFileSelectorDialog curseForgeProjectFileSelectorDialog = new CurseForgeProjectFileSelectorDialog(
                        this, castMod, instanceOrServer);
                    curseForgeProjectFileSelectorDialog.setVisible(true);
                }, e -> {
                    if (sectionValue.equals("Plugins")) {
                        Analytics.trackEvent(AnalyticsEvent.forRemovePlugin(castMod));
                    } else if (sectionValue.equals("Resource Packs")) {
                        Analytics.trackEvent(AnalyticsEvent.forRemoveResourcePack(castMod));
                    } else if (sectionValue.equals("Shaders")) {
                        Analytics.trackEvent(AnalyticsEvent.forRemoveShaders(castMod));
                    } else {
                        Analytics.trackEvent(AnalyticsEvent.forRemoveMod(castMod));
                    }

                    Optional<DisableableMod> foundMod = instanceOrServer.getMods().stream()
                        .filter(dm -> dm.isFromCurseForge() && dm.curseForgeProjectId == castMod.id)
                        .findFirst();

                    if (foundMod.isPresent()) {
                        instanceOrServer.removeMod(foundMod.get());

                        if (castMod.id == Constants.CURSEFORGE_FABRIC_MOD_ID) {
                            fabricApiWarningLabel.setVisible(true);
                            installFabricApiButton.setVisible(true);
                        }

                        if (castMod.id == Constants.CURSEFORGE_LEGACY_FABRIC_MOD_ID) {
                            legacyFabricApiWarningLabel.setVisible(true);
                            installLegacyFabricApiButton.setVisible(true);
                        }

                        if (castMod.id == Constants.CURSEFORGE_FORGIFIED_FABRIC_API_MOD_ID) {
                            forgifiedFabricApiWarningLabel.setVisible(true);
                            installForgifiedFabricApiButton.setVisible(true);
                        }
                    }
                }), gbc);

                gbc.gridy++;
            });
        }

        SwingUtilities.invokeLater(() -> jscrollPane.getVerticalScrollBar().setValue(0));

        revalidate();
        repaint();
    }

    private void setModrinthMods(ModrinthSearchResult searchResult) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets.set(2, 2, 2, 2);

        contentPanel.removeAll();

        if (searchResult == null || searchResult.hits.isEmpty()) {
            contentPanel.setLayout(new BorderLayout());
            contentPanel.add(new NoCurseModsPanel(!this.searchField.getText().isEmpty()), BorderLayout.CENTER);
        } else {
            prevButton.setEnabled(page > 0);
            nextButton.setEnabled((searchResult.offset + searchResult.limit) < searchResult.totalHits);

            contentPanel.setLayout(new WrapLayout());

            searchResult.hits.forEach(mod -> {
                ModrinthSearchHit castMod = mod;
                String sectionValue =
                    Optional.ofNullable((ComboItem<String>) sectionComboBox.getSelectedItem()).map(ComboItem::getValue)
                        .orElse("Mods");

                contentPanel.add(new ModrinthSearchHitCard(castMod, instanceOrServer, e -> {
                    final ProgressDialog<ModrinthProject> modrinthProjectLookupDialog = new ProgressDialog<>(
                        GetText.tr("Getting Mod Information"), 0, GetText.tr("Getting Mod Information"),
                        "Aborting Getting Mod Information");

                    modrinthProjectLookupDialog.addThread(new Thread(() -> {
                        modrinthProjectLookupDialog.setReturnValue(ModrinthApi.getProject(castMod.projectId));

                        modrinthProjectLookupDialog.close();
                    }));

                    modrinthProjectLookupDialog.start();

                    ModrinthProject modrinthMod = modrinthProjectLookupDialog.getReturnValue();

                    if (modrinthMod == null) {
                        DialogManager.okDialog().setTitle(GetText.tr("Error Getting Mod Information"))
                            .setContent(new HTMLBuilder().center().text(GetText.tr(
                                    "There was an error getting mod information from Modrinth. Please try again later."))
                                .build())
                            .setType(DialogManager.ERROR).show();
                        return;
                    }

                    if (sectionValue.equals("Plugins")) {
                        Analytics.trackEvent(AnalyticsEvent.forAddPlugin(castMod));
                    } else if (sectionValue.equals("Resource Packs")) {
                        Analytics.trackEvent(AnalyticsEvent.forAddResourcePack(castMod));
                    } else if (sectionValue.equals("Shaders")) {
                        Analytics.trackEvent(AnalyticsEvent.forAddShaders(castMod));
                    } else {
                        Analytics.trackEvent(AnalyticsEvent.forAddMod(castMod));
                    }

                    ModrinthVersionSelectorDialog modrinthVersionSelectorDialog = new ModrinthVersionSelectorDialog(
                        this, modrinthMod, instanceOrServer);
                    modrinthVersionSelectorDialog.setVisible(true);
                }, e -> {
                    if (sectionValue.equals("Plugins")) {
                        Analytics.trackEvent(AnalyticsEvent.forRemovePlugin(castMod));
                    } else if (sectionValue.equals("Resource Packs")) {
                        Analytics.trackEvent(AnalyticsEvent.forRemoveResourcePack(castMod));
                    } else if (sectionValue.equals("Shaders")) {
                        Analytics.trackEvent(AnalyticsEvent.forRemoveShaders(castMod));
                    } else {
                        Analytics.trackEvent(AnalyticsEvent.forRemoveMod(castMod));
                    }

                    Optional<DisableableMod> foundMod = instanceOrServer.getMods().stream()
                        .filter(dm -> dm.isFromModrinth() && dm.modrinthProject.id.equals(castMod.projectId))
                        .findFirst();

                    if (foundMod.isPresent()) {
                        instanceOrServer.removeMod(foundMod.get());

                        if (castMod.projectId.equals(Constants.MODRINTH_FABRIC_MOD_ID)) {
                            fabricApiWarningLabel.setVisible(true);
                            installFabricApiButton.setVisible(true);
                        }

                        if (castMod.projectId.equals(Constants.MODRINTH_LEGACY_FABRIC_MOD_ID)) {
                            legacyFabricApiWarningLabel.setVisible(true);
                            installLegacyFabricApiButton.setVisible(true);
                        }

                        if (castMod.projectId.equals(Constants.MODRINTH_QSL_MOD_ID)) {
                            quiltStandardLibrariesWarningLabel.setVisible(true);
                            installQuiltStandardLibrariesButton.setVisible(true);
                        }

                        if (castMod.projectId.equals(Constants.MODRINTH_FORGIFIED_FABRIC_API_MOD_ID)) {
                            forgifiedFabricApiWarningLabel.setVisible(true);
                            installForgifiedFabricApiButton.setVisible(true);
                        }
                    }
                }), gbc);

                gbc.gridy++;
            });
        }

        SwingUtilities.invokeLater(() -> jscrollPane.getVerticalScrollBar().setValue(0));

        revalidate();
        repaint();
    }

    private void addSectionAndSortOptions(boolean firstTime) {
        if (instanceOrServer.supportsPlugins()) {
            sectionComboBox.addItem(new ComboItem<>("Plugins", GetText.tr("Plugins")));
        }
        if (instanceOrServer.getLoaderVersion() != null && !instanceOrServer.getLoaderVersion().isPaper()
            && !instanceOrServer.getLoaderVersion().isPurpur()) {
            sectionComboBox.addItem(new ComboItem<>("Mods", GetText.tr("Mods")));
        }
        if (instanceOrServer instanceof Instance) {
            sectionComboBox.addItem(new ComboItem<>("Resource Packs", GetText.tr("Resource Packs")));
            if (!firstTime) {
                boolean resourcePacksSelected = ((ComboItem<String>) sectionComboBox.getSelectedItem()).getValue()
                    .equals("Resource Packs");

                if (resourcePacksSelected) {
                    sectionComboBox.setSelectedIndex(sectionComboBox.getItemCount() - 1);
                }
            }

            sectionComboBox.addItem(new ComboItem<>("Shaders", GetText.tr("Shaders")));
            if (!firstTime) {
                boolean shadersSelected = ((ComboItem<String>) sectionComboBox.getSelectedItem()).getValue()
                    .equals("Shaders");

                if (shadersSelected) {
                    sectionComboBox.setSelectedIndex(sectionComboBox.getItemCount() - 1);
                }
            }
        }

        boolean isCurseForgeSelected = (firstTime
            && (instanceOrServer.getLoaderVersion() == null || (!instanceOrServer.getLoaderVersion().isPaper()
            && !instanceOrServer.getLoaderVersion().isPurpur())))
            ? App.settings.defaultModPlatform == ModPlatform.CURSEFORGE
            : ((ComboItem<ModPlatform>) hostComboBox.getSelectedItem())
                .getValue() == ModPlatform.CURSEFORGE;

        if (isCurseForgeSelected) {
            if (instanceOrServer instanceof Instance) {
                sectionComboBox.addItem(new ComboItem<>("Worlds", GetText.tr("Worlds")));
            }

            sortComboBox.addItem(new ComboItem<>("Popularity", GetText.tr("Popularity")));
            sortComboBox.addItem(new ComboItem<>("Last Updated", GetText.tr("Last Updated")));
            sortComboBox.addItem(new ComboItem<>("Total Downloads", GetText.tr("Total Downloads")));
        } else {
            sortComboBox.addItem(new ComboItem<>("relevance", GetText.tr("Relevance")));
            sortComboBox.addItem(new ComboItem<>("newest", GetText.tr("Newest")));
            sortComboBox.addItem(new ComboItem<>("updated", GetText.tr("Last Updated")));
            sortComboBox.addItem(new ComboItem<>("downloads", GetText.tr("Total Downloads")));
        }

    }

    private void addCategories() {
        updating = true;
        categoriesComboBox.removeAllItems();

        categoriesComboBox.addItem(new ComboItem<>(null, GetText.tr("All Categories")));

        boolean isCurseForge = ((ComboItem<ModPlatform>) hostComboBox.getSelectedItem())
            .getValue() == ModPlatform.CURSEFORGE;

        if (isCurseForge) {
            List<CurseForgeCategoryForGame> categories = new ArrayList<>();

            if (((ComboItem<String>) sectionComboBox.getSelectedItem()).getValue().equals("Resource Packs")) {
                categories.addAll(CurseForgeApi.getCategoriesForResourcePacks());
            } else if (((ComboItem<String>) sectionComboBox.getSelectedItem()).getValue().equals("Shaders")) {
                categories.addAll(CurseForgeApi.getCategoriesForShaderPacks());
            } else if (((ComboItem<String>) sectionComboBox.getSelectedItem()).getValue().equals("Worlds")) {
                categories.addAll(CurseForgeApi.getCategoriesForWorlds());
            } else if (((ComboItem<String>) sectionComboBox.getSelectedItem()).getValue().equals("Plugins")) {
                categories.addAll(CurseForgeApi.getCategoriesForPlugins());
            } else {
                categories.addAll(CurseForgeApi.getCategoriesForMods());
            }

            categories.forEach(
                c -> categoriesComboBox.addItem(new ComboItem<>(String.valueOf(c.id), c.name)));
        } else {
            List<ModrinthCategory> categories = new ArrayList<>();

            if (((ComboItem<String>) sectionComboBox.getSelectedItem()).getValue().equals("Resource Packs")) {
                categories.addAll(ModrinthApi.getCategoriesForResourcePacks());
            } else if (((ComboItem<String>) sectionComboBox.getSelectedItem()).getValue().equals("Shaders")) {
                categories.addAll(ModrinthApi.getCategoriesForShaders());
            } else if (((ComboItem<String>) sectionComboBox.getSelectedItem()).getValue().equals("Plugins")) {
                categories.addAll(ModrinthApi.getCategoriesForPlugins());
            } else {
                categories.addAll(ModrinthApi.getCategoriesForMods());
            }

            categories.forEach(
                c -> categoriesComboBox.addItem(new ComboItem<>(c.name, Utils.capitalize(c.name))));
        }
        updating = false;
    }
}
