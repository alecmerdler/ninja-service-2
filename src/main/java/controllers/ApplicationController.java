/**
 * Copyright (C) 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dao.MealDao;
import dao.TagDao;
import models.Meal;
import ninja.Context;
import ninja.Result;
import ninja.exceptions.BadRequestException;
import ninja.params.Param;
import ninja.params.PathParam;
import org.hibernate.service.spi.ServiceException;
import rx.schedulers.Schedulers;
import services.MealService;
import services.MessageService;

import java.util.*;

import static ninja.Results.json;


@Singleton
public class ApplicationController {

    private final String serviceUrl = "http://localhost:8080";
    private final ObjectMapper objectMapper;
    private final MessageService messageService;
    private final MealService mealService;
    private final MealDao mealDao;
    private final TagDao tagDao;

    @Inject
    public ApplicationController(MealDao mealDao, TagDao tagDao, MessageService messageService, MealService mealService) {
        this.objectMapper = new ObjectMapper();
        this.messageService = messageService;
        this.mealService = mealService;
        this.mealDao = mealDao;
        this.tagDao = tagDao;
    }

    public Result initialize(Context context, Map<String, Object> options) {
        // TODO: Move to initialization service
        messageService.subscribe("users/+")
                .subscribeOn(Schedulers.newThread())
                .subscribe((Map<String, Object> message) -> {
                    if (message.containsKey("action") && message.get("action").equals("destroy")) {
                        Number userId = (Number) message.get("userId");
                        try {
                            List<Meal> mealsWithChefId = mealService.listMealsByChefId(userId.longValue());
                            for (Meal meal : mealsWithChefId) {
                                mealService.destroyMeal(meal);
                            }
                        } catch (ServiceException se) {
                            throw new BadRequestException(se.getMessage());
                        }
                    }
                });

        Map<String, Object> response = new HashMap<>();
        response.put("status", "initialized");

        return json()
                .status(200)
                .render(response);
    }

    public Result listMessages() {
        final List<Map<String, Object>> messages = new ArrayList<>();
        try {
            messageService.getMessages()
                    .subscribeOn(Schedulers.newThread())
                    .subscribe((List<Map<String, Object>> newMessages) -> {
                        for (Map<String, Object> message : newMessages) {
                            messages.add(message);
                        }
                    });
        } catch (Exception e) {
            throw new BadRequestException(e.getMessage());
        }

        return json()
                .status(200)
                .render(messages);
    }

    public Result listMeals(@Param("tagId") Long id) {
        List<Meal> allMeals = new ArrayList<>();
        try {
            if (id != null) {
                allMeals = mealDao.findByTagId(id);
            }
            else {
                allMeals = mealDao.findAll();
            }
        } catch (Exception e) {
            throw new BadRequestException(e.getMessage());
        }

        return json()
                .status(200)
                .render(allMeals);
    }

    public Result createMeal(Context context, Meal meal) {
        Meal createdMeal = null;
        try {
            Optional<Meal> mealOptional = mealService.createMeal(meal);
            if (mealOptional.isPresent()) {
                createdMeal = mealOptional.get();
            }
        } catch (ServiceException se) {
            throw new BadRequestException(se.getMessage());
        }

        return json()
                .status(201)
                .render(createdMeal);
    }

    public Result retrieveMeal(@PathParam("id") Long id) {
        Result response = json()
                .status(404)
                .render(new HashMap<>());
        try {
            Optional<Meal> mealOptional = mealService.retrieveMealById(id);
            if (mealOptional.isPresent()) {
                response = json()
                        .status(200)
                        .render(mealOptional.get());
            }
        } catch (ServiceException se) {
            throw new BadRequestException(se.getMessage());
        }

        return response;
    }

    public Result updateMeal(@PathParam("id") Long id, Context context, Meal updatedMeal) {
        throw new BadRequestException("Route not implemented");
    }

    public Result destroyMeal(@PathParam("id") Long id) {
        Result response = json()
                .status(404)
                .render(new HashMap<>());
        try {
            Optional<Meal> mealOptional = mealService.retrieveMealById(id);
            if (mealOptional.isPresent()) {
                boolean status = mealService.destroyMeal(mealOptional.get());
                response = json()
                        .status(204)
                        .render(new HashMap<>());
            }
        } catch (ServiceException se) {
            throw new BadRequestException(se.getMessage());
        }

        return response;
    }
}
