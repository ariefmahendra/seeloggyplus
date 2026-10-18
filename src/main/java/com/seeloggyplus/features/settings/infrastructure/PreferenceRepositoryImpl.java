package com.seeloggyplus.features.settings.infrastructure;

import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.shared.database.DatabaseConfig;
import com.seeloggyplus.shared.database.FatalDatabaseException;
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
    private final Connection connection;

    public PreferenceRepositoryImpl() {
        this.connection = null;
    }

    public PreferenceRepositoryImpl(Connection connection) {
        this.connection = connection;
    }

    private Connection getConnection() {
        return connection != null ? connection : DatabaseConfig.getInstance().getConnection();
    }

    @Override
    public void savePreferences(Preference preferences) {
        String sql = "INSERT INTO preferences(code, value) VALUES(?, ?)";
        try (PreparedStatement preparedStatement = getConnection().prepareStatement(sql)){
            preparedStatement.setString(1, preferences.getCode());
            preparedStatement.setString(2, preferences.getValue());

            preparedStatement.execute();
        } catch (SQLException ex){
            logger.error("error when save preferences: ", ex);
            throw new FatalDatabaseException("Error saving preference: " + preferences.getCode(), ex);
        }
    }

    @Override
    public void saveOrUpdatePreferences(Preference preferences) {
        String sql = "INSERT INTO preferences(code, value) VALUES(?, ?) ON CONFLICT DO UPDATE SET value = ?";
        try (PreparedStatement preparedStatement = getConnection().prepareStatement(sql)){
            preparedStatement.setString(1, preferences.getCode());
            preparedStatement.setString(2, preferences.getValue());
            preparedStatement.setString(3, preferences.getValue());

            preparedStatement.execute();
        } catch (SQLException ex){
            logger.error("error when save or update preferences: ", ex);
            throw new FatalDatabaseException("Error saving preference: " + preferences.getCode(), ex);
        }
    }

    @Override
    public void updatePreferences(Preference preferences) {
        String sql = "UPDATE preferences SET value = ? WHERE code = ?";
        try (PreparedStatement preparedStatement = getConnection().prepareStatement(sql)){
            preparedStatement.setString(1, preferences.getValue());
            preparedStatement.setString(2, preferences.getCode());
            preparedStatement.execute();
        }catch (SQLException ex){
            logger.error("error when update preferences: ", ex);
            throw new FatalDatabaseException("Error updating preference: " + preferences.getCode(), ex);
        }
    }

    @Override
    public Optional<String> getPreferencesByCode(String code) {
        String sql = "SELECT value FROM preferences WHERE code = ?";
        try (PreparedStatement preparedStatement = getConnection().prepareStatement(sql)){
            preparedStatement.setString(1, code);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()){
                return Optional.ofNullable(resultSet.getString("value"));
            }
            return Optional.empty();
        }catch (SQLException ex){
            logger.error("error when get preference by key: ", ex);
            throw new FatalDatabaseException("Error reading preference: " + code, ex);
        }
    }

    @Override
    public List<Preference> getListPreferences() {
        List<Preference> preferencesList = new ArrayList<>();
        String sql = "SELECT code, value FROM preferences";
        try (PreparedStatement preparedStatement = getConnection().prepareStatement(sql)){
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()){
                Preference preferences = new Preference();
                preferences.setCode(resultSet.getString("code"));
                preferences.setValue(resultSet.getString("value"));
                preferencesList.add(preferences);
            }
        }catch (SQLException e){
            logger.error("error when get list preferences: ", e);
            throw new FatalDatabaseException("Error listing preferences", e);
        }

        return preferencesList;
    }
}
