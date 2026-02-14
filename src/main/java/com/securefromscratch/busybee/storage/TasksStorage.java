package com.securefromscratch.busybee.storage;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.nio.file.Path;

@Service
public class TasksStorage {
    private final List<Task> m_tasks = loadTasks();

    public TasksStorage() throws IOException, ClassNotFoundException {
        // Use initial hardcoded values if the file does not exist or is empty
        if (m_tasks.isEmpty()) {
            InitialDataGenerator.fillWithData(m_tasks);
        }
    }

    public List<Task> getAll() {
        return Collections.unmodifiableList(m_tasks);
    }

    public UUID add(String name, String desc, String createdBy, String[] responsibilityOf) throws IOException {
        Task newTask = new Task(name, desc, createdBy, responsibilityOf);
        return add(newTask);
    }

    public UUID add(String name, String desc, LocalDate dueDate, String createdBy, String[] responsibilityOf) throws IOException {
        Task newTask = new Task(name, desc, dueDate, createdBy, responsibilityOf);
        return add(newTask);
    }

    public UUID add(String name, String desc, LocalDate dueDate, LocalTime dueTime, String createdBy, String[] responsibilityOf) throws IOException {
        Task newTask = new Task(name, desc, dueDate, dueTime, createdBy, responsibilityOf);
        return add(newTask);
    }

    public boolean markDone(UUID taskid) throws IOException {
        Iterator<Task> tasksItr = m_tasks.iterator();
        while (tasksItr.hasNext()) {
            Task t = tasksItr.next();
            if (t.taskid().equals(taskid)) {
                if (t.done()) {
                    return true;
                }
                tasksItr.remove();
                Task doneTask = Task.asDone(t);
                m_tasks.add(doneTask);
                saveTasks();
                return false;
            }
        }
        throw new TaskNotFoundException(taskid);
    }

    public UUID add(Task newTask) throws IOException {
        m_tasks.add(newTask);
        saveTasks();
        return newTask.taskid();
    }

    private List<Task> loadTasks() {
        // NOT IMPLEMENTED
        // TODO: You will need to implement this
        return new ArrayList<>();
    }

    private void saveTasks() throws IOException {
        // NOT IMPLEMENTED
        // TODO: You will need to implement this
    }

    public UUID addComment(Task t, String text, String createdBy, Optional<UUID> after) throws IOException {
        UUID commentId = t.addComment(text, createdBy, after);
        saveTasks();
        return commentId;
    }

    public UUID addComment(Task t, String text, Optional<String> image, Optional<String> attachment, String createdBy, Optional<UUID> after) throws IOException {
        UUID commentId = t.addComment(text, image, attachment, createdBy, after);
        saveTasks();
        return commentId;
    }

    public UUID addCommentWithOptionalUpload(Task t, String text, Optional<MultipartFile> optFile, String createdBy, Optional<UUID> after) throws IOException {
        if (optFile == null || optFile.isEmpty() || optFile.get() == null || optFile.get().isEmpty()) {
            return addComment(t, text, createdBy, after);
        }

        MultipartFile file = optFile.get();
        FileStorage storage = new FileStorage(Path.of("uploads").toAbsolutePath().normalize());
        String storedFilename = storage.storeUpload(file, createdBy);

        FileStorage.FileType filetype = FileStorage.identifyType(file);
        Optional<String> imageFilename = (filetype == FileStorage.FileType.IMAGE) ? Optional.of(storedFilename) : Optional.empty();
        Optional<String> attachFilename = (filetype != FileStorage.FileType.IMAGE) ? Optional.of(storedFilename) : Optional.empty();

        try {
            return addComment(t, text, imageFilename, attachFilename, createdBy, after);
        } catch (IOException | RuntimeException ex) {
            storage.cleanupStoredUpload(storedFilename);
            throw ex;
        }
    }

    public Optional<Task> find(UUID taskid) {
        return m_tasks.stream().filter((other)->other.taskid().equals(taskid)).findAny();
    }

    public boolean taskNameExists(String name) {
        if (name == null) {
            return false;
        }
        String normalizedName = name.trim();
        if (normalizedName.isEmpty()) {
            return false;
        }

        return m_tasks.stream()
                .map(Task::name)
                .filter(Objects::nonNull)
                .anyMatch(existing -> existing.trim().equalsIgnoreCase(normalizedName));
    }
}
