/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.common.verbose;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import me.lucko.luckperms.api.Tristate;
import me.lucko.luckperms.common.commands.sender.Sender;
import me.lucko.luckperms.common.locale.Message;
import me.lucko.luckperms.common.utils.DateUtil;
import me.lucko.luckperms.common.utils.PasteUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Accepts and processes {@link CheckData}, passed from the {@link VerboseHandler}.
 */
@RequiredArgsConstructor
public class VerboseListener {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");

    // how much data should we store before stopping.
    private static final int DATA_TRUNCATION = 10000;

    // the time when the listener was first registered
    private final long startTime = System.currentTimeMillis();

    // the version of the plugin. (used when we paste data to gist)
    private final String pluginVersion;

    // the sender to notify each time the listener processes a check which passes the filter
    @Getter
    private final Sender notifiedSender;

    // the filter string
    private final String filter;

    // if we should notify the sender
    private final boolean notify;

    // the number of checks we have processed
    private final AtomicInteger counter = new AtomicInteger(0);

    // the number of checks we have processed and accepted, based on the filter rules for this
    // listener
    private final AtomicInteger matchedCounter = new AtomicInteger(0);

    // the checks which passed the filter, up to a max size of #DATA_TRUNCATION
    private final List<CheckData> results = new ArrayList<>(DATA_TRUNCATION / 10);

    /**
     * Accepts and processes check data.
     *
     * @param data the data to process
     */
    public void acceptData(CheckData data) {
        counter.incrementAndGet();
        if (!VerboseFilter.passesFilter(data, filter)) {
            return;
        }
        matchedCounter.incrementAndGet();

        if (results.size() < DATA_TRUNCATION) {
            results.add(data);
        }

        if (notify) {
            Message.VERBOSE_LOG.send(notifiedSender, "&a" + data.getCheckTarget() + "&7 -- &a" + data.getPermission() + "&7 -- " + getTristateColor(data.getResult()) + data.getResult().name().toLowerCase() + "");
        }
    }

    /**
     * Uploads the captured data in this listener to a paste and returns the url
     *
     * @return the url
     * @see PasteUtils#paste(String, List)
     */
    public String uploadPasteData() {
        long now = System.currentTimeMillis();
        String startDate = DATE_FORMAT.format(new Date(startTime));
        String endDate = DATE_FORMAT.format(new Date(now));
        long secondsTaken = (now - startTime) / 1000L;
        String duration = DateUtil.formatTime(secondsTaken);

        String filter = this.filter;
        if (filter == null || filter.equals("")){
            filter = "any";
        } else {
            filter = "`" + filter + "`";
        }

        ImmutableList.Builder<String> prettyOutput = ImmutableList.<String>builder()
                .add("## Verbose Checking Output")
                .add("#### This file was automatically generated by [LuckPerms](https://github.com/lucko/LuckPerms) " + pluginVersion)
                .add("")
                .add("### Metadata")
                .add("| Key | Value |")
                .add("|-----|-------|")
                .add("| Start Time | " + startDate + " |")
                .add("| End Time | " + endDate + " |")
                .add("| Duration | " + duration +" |")
                .add("| Count | **" + matchedCounter.get() + "** / " + counter + " |")
                .add("| User | " + notifiedSender.getName() + " |")
                .add("| Filter | " + filter + " |")
                .add("");

        if (matchedCounter.get() > results.size()) {
            prettyOutput.add("**WARN:** Result set exceeded max size of " + DATA_TRUNCATION + ". The output below was truncated to " + DATA_TRUNCATION + " entries.");
            prettyOutput.add("");
        }

        prettyOutput.add("### Output")
                .add("Format: `<checked>` `<permission>` `<value>`")
                .add("")
                .add("___")
                .add("");

        ImmutableList.Builder<String> csvOutput = ImmutableList.<String>builder()
                .add("User,Permission,Result");

        results.forEach(c -> {
            prettyOutput.add("`" + c.getCheckTarget() + "` - " + c.getPermission() + " - **" + c.getResult().toString() + "**   ");
            csvOutput.add(escapeCommas(c.getCheckTarget()) + "," + escapeCommas(c.getPermission()) + "," + c.getResult().name().toLowerCase());
        });
        results.clear();

        List<Map.Entry<String, String>> content = ImmutableList.of(
                Maps.immutableEntry("luckperms-verbose.md", prettyOutput.build().stream().collect(Collectors.joining("\n"))),
                Maps.immutableEntry("raw-data.csv", csvOutput.build().stream().collect(Collectors.joining("\n")))
        );

        return PasteUtils.paste("LuckPerms Verbose Checking Output", content);
    }

    private static String escapeCommas(String s) {
        return s.contains(",") ? "\"" + s + "\"" : s;
    }

    private static String getTristateColor(Tristate tristate) {
        switch (tristate) {
            case TRUE:
                return "&2";
            case FALSE:
                return "&c";
            default:
                return "&7";
        }
    }

}
