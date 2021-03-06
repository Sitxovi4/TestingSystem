package com.testingSystem.model.services;

import com.google.gson.Gson;
import com.testingSystem.model.dao.StatisticDao;
import com.testingSystem.model.dao.TestDao;
import com.testingSystem.model.dao.UserDao;
import com.testingSystem.model.entity.Statistic;
import com.testingSystem.model.entity.Test;
import com.testingSystem.model.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class UserProgressGridService implements CalculatePercentage {

    public static final class UserGrid{
        private String testName;
        private Integer testId;
        private List<Integer> percentList;

        UserGrid(String testName,Integer testId, List<Integer> percentList) {
            this.testName = testName;
            this.testId = testId;
            this.percentList = percentList;
        }

        public String getTestName() {
            return testName;
        }

        public List<Integer> getPercentList() {
            return percentList;
        }

        public Integer getTestId() {
            return testId;
        }

        public void setTestName(String testName) {
            this.testName = testName;
        }

        public void setPercentList(List<Integer> percentList) {
            this.percentList = percentList;
        }

        public void setTestId(Integer testId) {
            this.testId = testId;
        }
    }

    private TestDao testDao;
    private StatisticDao statisticDao;
    private UserDao userDao;

    @Autowired
    public UserProgressGridService(TestDao testDao, StatisticDao statisticDao, UserDao userDao) {
        this.testDao = testDao;
        this.statisticDao = statisticDao;
        this.userDao = userDao;
    }

    /**
     * @param statistics Принимает список статистики только конкретного теста ( testId ) !!!
     * и группирует по дате прохождения теста
     * @return результаты прохожднеия тестов в процентах по датам
     */
    private List<Integer> getPercentList(List<Statistic> statistics){

        List<Integer> percentList = new ArrayList<>();
        // Группируем статистику по дате прохождения теста
        // На всякий случай ключи будут уже в отсортированном порядке для сохрнения хронологии
        Map<Date, List<Statistic>> statisticTreeMapByData = statistics.stream().collect(
                Collectors.groupingBy(Statistic::getDate, HashMap::new, Collectors.toCollection(ArrayList::new)));

        for (Date date: statisticTreeMapByData.keySet()){

            List<Statistic> statisticList = statisticTreeMapByData.get(date);
            percentList.add(calculatePercentage(statisticList));
        }
        return percentList;
    }

    public String getUserProgressGrid(Integer userId){
        // Список кастомных объектов для отображения
        List<UserGrid> userProgressGridList = new ArrayList<>();
       // List<UserGrid> userProgressGridListFinal = new ArrayList<>();

        // Карта всех тестов и их названий, чтобы каждый раз не обращаться в бд
        Map<Integer, String> mapTestIdName =  testDao.getAllTests().stream().collect(
                Collectors.toMap(Test::getTestId,Test::getTestName));

        // Сначала надо сгруппировать статистику по testId (в TreeMap. это гарантируе хронологию), а потом уже по дате прохождения теста
        Map<Integer,List<Statistic>> statisticMapByTestId = statisticDao.getAllStatisticByUserId(userId).stream().collect(
                Collectors.groupingBy(Statistic::getTestId, HashMap::new, Collectors.toCollection(ArrayList::new)));

        for (Integer testId: statisticMapByTestId.keySet()) {
            // получить лист всей статистики по testId и отдать другой функции
            List<Integer> percentList = getPercentList(statisticMapByTestId.get(testId));
            // накапливаем прогресс в лист. (testName, testId, {%,%,%,% по дням}  )
            userProgressGridList.add(new UserGrid(mapTestIdName.get(testId), testId, percentList));
        }

        Map<String, UserGrid> mapTestNameUserGrid = userProgressGridList.stream()
                .collect(Collectors.toMap(UserGrid::getTestName,o -> o,
                        (oldVal,newVal) -> {
                            oldVal.percentList.addAll(newVal.percentList);
                            return oldVal;
                        }));

        userProgressGridList.clear();
        List<Integer> allPercents = new ArrayList<>();
        for (String testName : mapTestNameUserGrid.keySet()){
            UserGrid userGrid = mapTestNameUserGrid.get(testName);
                // добавление {%,%,%,%}, {%,%,%,%} и т.д в один общий с учётом хронологии
                allPercents.addAll(userGrid.getPercentList());

            userProgressGridList.add(new UserGrid(testName,null, new ArrayList<>(allPercents)));
            // очистить лист перед переходом к следующему имени теста
            allPercents.clear();
        }

        Gson gson = new Gson();
        return gson.toJson(userProgressGridList);
    }
    public List<User> getAllUsers(){
        return userDao.getAllUsers();
    }
}
