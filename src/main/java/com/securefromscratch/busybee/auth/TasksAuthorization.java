package com.securefromscratch.busybee.auth;

import java.util.Collection;
import java.util.UUID;
import java.util.Optional;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.securefromscratch.busybee.controllers.TaskOut;
import com.securefromscratch.busybee.storage.TasksStorage;
import com.securefromscratch.busybee.storage.Task;
import com.securefromscratch.busybee.storage.TaskComment;
import com.securefromscratch.busybee.storage.TaskNotFoundException;

@Component("tasksAuthorization")
public class TasksAuthorization {
    @Autowired
    private TasksStorage m_tasks;

    // Allow TRIAL user to create a task only if they have no active (not done) tasks
    public boolean trialUserCanCreate(String username) {
        return m_tasks.getAll().stream()
                .filter(task -> task.createdBy().equals(username))
                .noneMatch(task -> !task.done());
    }

    // Checks if the user is the owner (creator) of the task with the given id
    public boolean isOwner(UUID taskid, String username) {
        Optional<Task> task = m_tasks.find(taskid);
        return task.isPresent() && task.get().createdBy().equals(username);
    }

    // Optional rule: allow users in responsibilityOf to close the task as well.
    public boolean isOwnerOrResponsible(UUID taskid, String username) {
        Optional<Task> taskOpt = m_tasks.find(taskid);
        if (taskOpt.isEmpty()) {
            throw new TaskNotFoundException(taskid);
        }

        Task task = taskOpt.get();
        if (task.createdBy().equals(username)) {
            return true;
        }

        String[] responsible = task.responsibilityOf();
        return responsible != null && Arrays.asList(responsible).contains(username);
    }

    public static Collection<TaskOut> filterToAuthorizedTasks(Collection<TaskOut> allTasks, String username) {
        return allTasks.stream()
                .filter(taskOut -> userAllowedToViewTask(taskOut, username))
                .toList();
    }

    public static boolean userAllowedToViewTask(TaskOut t, String username) {
        return t.createdBy().equals(username)
                || java.util.Arrays.stream(t.responsibilityOf()).anyMatch((String responsible) -> username.equals(responsible));
    }

    private static boolean userAllowedToViewTask(Task t, String username) {
        return t.createdBy().equals(username)
                || java.util.Arrays.stream(t.responsibilityOf()).anyMatch((String responsible) -> username.equals(responsible));
    }

    public boolean imgIsInOwnedOrAssignedTask(String imgName, String currentUser) {
        for (Task t : m_tasks.getAll()) {
            if (!userAllowedToViewTask(t, currentUser)) {
                continue;
            }
            for (TaskComment c : t.comments()) {
                if (c.image().isPresent() && c.image().get().equals(imgName)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean imageIsInOwnedOrAssignedTask(String imgName, String currentUser) {
        return imgIsInOwnedOrAssignedTask(imgName, currentUser);
    }

    public boolean attachmentIsInOwnedOrAssignedTask(String filename, String currentUser) {
        for (Task t : m_tasks.getAll()) {
            if (!userAllowedToViewTask(t, currentUser)) {
                continue;
            }
            for (TaskComment c : t.comments()) {
                if (c.attachment().isPresent() && c.attachment().get().equals(filename)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean userAllowedToComment(UUID taskid, String username) {
        Optional<Task> task = m_tasks.find(taskid);
        return task.isPresent() && userAllowedToViewTask(task.get(), username);
    }

    // SPRING adds the prefix ROLE_
    // ADMIN -> ROLE_ADMIN
    // CREATOR -> ROLE_CREATOR
    public static boolean containsRole(Collection<? extends org.springframework.security.core.GrantedAuthority> authorities, String[] roles) {
        java.util.List<String> prefixedRoles = new java.util.ArrayList<>(roles.length);
        for (String r : roles) {
            prefixedRoles.add("ROLE_" + r);
        }
        for (org.springframework.security.core.GrantedAuthority a : authorities) {
            if (prefixedRoles.stream().anyMatch(val -> val.equals(a.getAuthority()))) {
                return true;
            }
        }
        return false;
    }
}
