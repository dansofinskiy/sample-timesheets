/*
 * Copyright (c) 2015 com.haulmont.timesheets.gui
 */
package com.haulmont.timesheets.gui.weeklytimesheets;

import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.DataManager;
import com.haulmont.cuba.core.global.LoadContext;
import com.haulmont.cuba.core.global.Messages;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.data.impl.DsListenerAdapter;
import com.haulmont.cuba.gui.xml.layout.ComponentsFactory;
import com.haulmont.cuba.security.global.UserSession;
import com.haulmont.timesheets.entity.DayOfWeek;
import com.haulmont.timesheets.entity.Project;
import com.haulmont.timesheets.entity.Task;
import com.haulmont.timesheets.entity.WeeklyReportEntry;
import com.haulmont.timesheets.gui.ComponentsHelper;
import org.apache.commons.lang.time.DateUtils;

import javax.inject.Inject;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author gorelov
 */
public class SimpleWeeklyTimesheets extends AbstractWindow {
    @Inject
    protected Table weeklyTsTable;
    @Inject
    protected CollectionDatasource<WeeklyReportEntry, UUID> weeklyEntriesDs;
    @Inject
    protected ComponentsFactory componentsFactory;
    @Inject
    protected DataManager dataManager;
    @Inject
    protected UserSession userSession;
    @Inject
    protected Label weekLabel;
    @Inject
    protected Messages messages;

    protected Map<Project, Map<String, Object>> lookupFieldsOptionsLists = new HashMap<>();
    protected Date firstDayOfWeek;
    protected DateFormat dateFormat;

    @Override
    public void init(Map<String, Object> params) {
        firstDayOfWeek = getFirstDayOfWeek();
        dateFormat = new SimpleDateFormat(messages.getMainMessage("dateFormat"));
        updateWeekLabel();

        weeklyTsTable.addAction(new ComponentsHelper.CaptionlessRemoveAction(weeklyTsTable));
        weeklyTsTable.addGeneratedColumn("task", new Table.ColumnGenerator() {
            @Override
            public Component generateCell(Entity entity) {
                @SuppressWarnings("unchecked")
                Datasource<WeeklyReportEntry> ds = (Datasource<WeeklyReportEntry>) weeklyTsTable.getItemDatasource(entity);
                final LookupField lookupField = componentsFactory.createComponent(LookupField.NAME);
                lookupField.setDatasource(ds, "task");
                lookupField.setWidth("100%");

                ds.addListener(new DsListenerAdapter<WeeklyReportEntry>() {
                    @Override
                    public void valueChanged(WeeklyReportEntry source, String property, Object prevValue, Object value) {
                        if ("project".equals(property)) {
                            Project project = (Project) value;
                            lookupField.setValue(null);
                            lookupField.setOptionsMap(getAssignedTasks(project));
                        }
                    }
                });

                final Project project = ds.getItem().getProject();
                if (project != null) {
                    Map<String, Object> tasks = getAssignedTasks(project);
                    lookupField.setOptionsMap(tasks);
                }
                return lookupField;
            }
        });

        for (final DayOfWeek day : DayOfWeek.values()) {
            weeklyTsTable.addGeneratedColumn(day.getId(), new Table.ColumnGenerator() {
                @Override
                public Component generateCell(Entity entity) {
                    WeeklyReportEntry reportEntry = (WeeklyReportEntry) entity;
                    if (reportEntry.getDayOfWeekTimeEntry(day) == null) {
                        TimeField timeField = componentsFactory.createComponent(TimeField.NAME);
                        timeField.setDatasource(weeklyTsTable.getItemDatasource(entity), day.getId() + "Time");
                        return timeField;
                    } else {
                        return null;
                    }
                }
            });
            weeklyTsTable.setColumnWidth(day.getId(), 80);
        }
    }

    public void addReport() {
        weeklyEntriesDs.addItem(new WeeklyReportEntry());
    }

    public void submitAll() {

    }

    protected Date getFirstDayOfWeek() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        return calendar.getTime();
    }

    public void movePreviousWeek() {
        firstDayOfWeek = DateUtils.addDays(firstDayOfWeek, -7);
        updateWeekLabel();
    }

    public void moveNextWeek() {
        firstDayOfWeek = DateUtils.addDays(firstDayOfWeek, 7);
        updateWeekLabel();
    }

    protected void updateWeekLabel() {
        weekLabel.setValue(String.format("%s - %s",
                dateFormat.format(firstDayOfWeek),
                dateFormat.format(DateUtils.addDays(firstDayOfWeek, 6))));
    }

    protected Map<String, Object> getAssignedTasks(Project project) {
        Map<String, Object> tasksMap = lookupFieldsOptionsLists.get(project);
        if (tasksMap == null) {
            LoadContext loadContext = new LoadContext(Task.class)
                    .setView("task-full");
            loadContext.setQueryString("select e from ts$Task e join e.participants p where p.user.id = :userId and e.project.id = :projectId and e.status = 10 order by e.project")
                    .setParameter("projectId", project.getId())
                    .setParameter("userId", userSession.getUser().getId());
            List<Task> taskList = dataManager.loadList(loadContext);
            tasksMap = new HashMap<>(taskList.size());
            for (Task task : taskList) {
                tasksMap.put(task.getName(), task);
            }
            lookupFieldsOptionsLists.put(project, tasksMap);
        }
        return tasksMap;
    }
}