package com.seeloggyplus.repository.impl;

import com.seeloggyplus.model.Preference;
import com.seeloggyplus.repository.PreferenceRepository;
import com.seeloggyplus.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PreferenceRepositoryImpl implements PreferenceRepository {
    private static final Logger logger = LoggerFactory.getLogger(PreferenceRepositoryImpl.class);
    private final Connection connection = DatabaseConfig.getInstance().getConnection();

    @Override
    public void savePreferences(Preference preferences) {
        String sql = "INSERT INTO preferences(code, value) VALUES(?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)){
            preparedStatement.setString(1, preferences.getCode());
            preparedStatement.setString(2, preferences.getValue());

            preparedStatement.execute();
        } catch (SQLException ex){
            logger.error("error when save preferences: ", ex);
        }
    }

    @Override
    public void saveOrUpdatePreferences(Preference preferences) {
        String sql = "INSERT INTO preferences(code, value) VALUES(?, ?) ON CONFLICT DO UPDATE SET value = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)){
            preparedStatement.setString(1, preferences.getCode());
            preparedStatement.setString(2, preferences.getValue());
            preparedStatement.setString(3, preferences.getValue());

            preparedStatement.execute();
        } catch (SQLException ex){
            logger.error("error when save or update preferences: ", ex);
        }
    }

    @Override
    public void updatePreferences(Preference preferences) {
        String sql = "UPDATE preferences SET value = ? WHERE code = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)){
            preparedStatement.setString(1, preferences.getCode());
            preparedStatement.setString(2, preferences.getValue());
            preparedStatement.execute();
        }catch (SQLException ex){
            logger.error("error when update preferences: ", ex);
        }
    }

    @Override
    public Optional<String> getPreferencesByCode(String code) {
        String value = "";
        String sql = "SELECT value FROM preferences WHERE code = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)){
            preparedStatement.setString(1, code);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()){
                value = (resultSet.getString("value"));
            }
        }catch (SQLException ex){
            logger.error("error when get preference by key: ", ex);
        }

        return Optional.of(value);
    }

    @Override
    public List<Preference> getListPreferences() {
        List<Preference> preferencesList = new ArrayList<>();
        String sql = "SELECT code, value FROM preferences";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)){
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()){
                Preference preferences = new Preference();
                preferences.setCode(resultSet.getString("code"));
                preferences.setValue(resultSet.getString("value"));
                preferencesList.add(preferences);
            }
        }catch (SQLException e){
            logger.error("error when get list preferences: ", e);
        }

        return preferencesList;
    }
}
